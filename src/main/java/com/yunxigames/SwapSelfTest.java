package com.yunxigames;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * 随机换位包的开服自检（真服务器，无玩家也能复用）。
 *
 * <p>自检编号是<b>包内局部</b>的（其它包也会有自己的 ①②③），按步骤名读日志。
 *
 * <p>覆盖思路：换位的核心是「位置真的换了、摔落真的清了、不该选的真的不选」——
 * 全部可以在纯服务端用盔甲架 + 一只猪验证，不需要玩家在线。
 * 开关值只回显不断言（自检修的是「链路能不能用」，不是「配置必须开着」）。
 */
public final class SwapSelfTest {

	private SwapSelfTest() {
	}

	/** 入口（包入口在 onInitialize 注册）。 */
	public static void checkAll(ServerLevel level, BlockPos pos, SwapConfig config) {
		checkConfig(config);
		checkSwapMovesAndResets(level, pos, config);
		checkPickExcludes(level, pos, config);
		checkTriggerGate(level, pos, config);
	}

	/** ① 配置回显 + 加载后的值合法（钳制生效）。 */
	private static void checkConfig(SwapConfig config) {
		Yg.LOGGER.info("[yg-swap] 换位开关={} 概率={} 半径={} 含玩家={} 生物可触发={} 清摔落={} 播报={}",
				config.hurtSwapEnabled, config.hurtSwapChance, config.hurtSwapMaxRadius,
				config.hurtSwapIncludePlayers, config.hurtSwapMobsCanTrigger,
				config.hurtSwapClearFallDistance, config.hurtSwapAnnounce);

		boolean ok = config.hurtSwapChance > 0.0f && config.hurtSwapChance <= 1.0f
				&& config.hurtSwapMaxRadius >= 8.0 && config.hurtSwapMaxRadius <= 256.0;
		SelfTest.check("① 换位·配置钳制", ok,
				"chance=" + config.hurtSwapChance + " radius=" + SelfTest.trim(config.hurtSwapMaxRadius));
	}

	/**
	 * ② 互换链路：两个实体互换后位置确实对调、摔落距离清零。
	 * 用盔甲架直调 {@link HurtSwap#applySwap}（applySwap 不做对象筛选，位置语义对任何实体一致）。
	 */
	private static void checkSwapMovesAndResets(ServerLevel level, BlockPos pos, SwapConfig config) {
		ArmorStand a = SelfTest.spawnArmorStand(level, pos);
		ArmorStand b = SelfTest.spawnArmorStand(level, pos.east(5));

		if (a == null || b == null) {
			SelfTest.check("② 换位·互换+清摔落", false, "造不出盔甲架，环境不可测");
			return;
		}

		try {
			Vec3 pa = a.position();
			Vec3 pb = b.position();
			a.fallDistance = 12.0;
			b.fallDistance = 3.0;

			HurtSwap.applySwap(level, a, b, config);

			boolean moved = a.position().distanceToSqr(pb) < 0.01 && b.position().distanceToSqr(pa) < 0.01;
			boolean cleared = a.fallDistance == 0.0 && b.fallDistance == 0.0;

			SelfTest.check("② 换位·互换+清摔落", moved && cleared,
					"位置对调=" + moved + " 摔落清零=" + cleared
							+ "（清摔落开关=" + config.hurtSwapClearFallDistance + "）");
		} finally {
			a.discard();
			b.discard();
		}
	}

	/**
	 * ③ 对象筛选：周围只有盔甲架时不选（盔甲架是装饰品）；黑名单 id 命中；
	 * 距离上限把半格外的合格对象挡在门外。用一只猪当「合格对象」（盔甲架被排除，
	 * 测距离必须用真活体；走注册表造，不 import 动物类 —— 26.2 动物包结构变动大）。
	 */
	private static void checkPickExcludes(ServerLevel level, BlockPos pos, SwapConfig config) {
		ArmorStand hurt = SelfTest.spawnArmorStand(level, pos);
		if (hurt == null) {
			SelfTest.check("③ 换位·对象筛选", false, "造不出盔甲架，环境不可测");
			return;
		}

		boolean armorStandExcluded;
		try {
			armorStandExcluded = HurtSwap.pickPartner(level, hurt, config) == null;
		} finally {
			hurt.discard();
		}

		boolean blacklistHit = config.isEntityBlacklisted(Identifier.parse("minecraft:wither"));

		// 距离上限：半径压到 8（钳制下限），猪放在 16 格外，必须选不到
		SwapConfig tinyRadius = SwapConfig.blankForTest();
		tinyRadius.hurtSwapMaxRadius = 8.0;

		LivingEntity pig = HurtSwap.spawnById(level, "minecraft:pig",
				new Vec3(pos.getX() + 16.5, pos.getY(), pos.getZ() + 0.5));
		boolean distanceLimited;
		try {
			ArmorStand probe = SelfTest.spawnArmorStand(level, pos);
			if (probe == null || pig == null) {
				distanceLimited = false;
			} else {
				try {
					distanceLimited = HurtSwap.pickPartner(level, probe, tinyRadius) == null;
				} finally {
					probe.discard();
				}
			}
		} finally {
			if (pig != null) {
				pig.discard();
			}
		}

		SelfTest.check("③ 换位·对象筛选", armorStandExcluded && blacklistHit && distanceLimited,
				"盔甲架不选=" + armorStandExcluded + " 黑名单命中=" + blacklistHit
						+ " 距离上限生效=" + distanceLimited);
	}

	/**
	 * ④ 触发门槛（1.0.1 定版）：生物受伤不触发换位，开关打开后恢复触发。
	 * 玩家路径无法在空服造真玩家，触发门槛之外的「玩家受伤→换位」链路只能进服目视确认
	 * （门槛之外的传送/清摔落/筛选逻辑已由 ②③ 覆盖）。
	 */
	private static void checkTriggerGate(ServerLevel level, BlockPos pos, SwapConfig config) {
		LivingEntity pig = HurtSwap.spawnById(level, "minecraft:pig",
				new Vec3(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5));
		if (pig == null) {
			SelfTest.check("④ 换位·触发门槛", false, "造不出猪，环境不可测");
			return;
		}

		boolean mobBlocked;
		try {
			mobBlocked = !HurtSwap.shouldTrigger(pig, config);
		} finally {
			pig.discard();
		}

		// 开关打开后生物恢复触发资格（1.0.0 混沌模式的兼容路径还在）
		SwapConfig chaos = SwapConfig.blankForTest();
		chaos.hurtSwapMobsCanTrigger = true;
		boolean mobAllowedWhenEnabled = HurtSwap.shouldTrigger(pig, chaos);

		SelfTest.check("④ 换位·触发门槛", mobBlocked && mobAllowedWhenEnabled,
				"生物默认不触发=" + mobBlocked + " 开关放行=" + mobAllowedWhenEnabled);
	}
}
