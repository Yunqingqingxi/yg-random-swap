package com.yunxigames;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 受伤随机互换位置（yg_swap 的全部玩法）。
 *
 * <p><b>是什么</b>：玩家掉血就直接与「附近随机一个活体」—— 生物或其他玩家 ——
 * 瞬间互换位置。摔落、岩浆、被咬，只要掉血就换，没有任何概率与来源判定。
 * 打僵尸正欢突然和 40 格外的苦力怕对视 —— 这就是本包的全部内容，纯混沌喜剧。
 *
 * <p><b>触发规则一句话</b>：只有<b>玩家掉血</b>才换（生物受伤不触发、生物之间不互换）。
 * 没有概率掷骰、没有伤害来源白名单 —— 那些判定方式实测只制造「明明该换却没换」的困惑。
 *
 * <p><b>为什么挂 AFTER_DAMAGE 而不是 ALLOW_DAMAGE</b>：换位放在伤害结算之后，
 * 不参与「这次伤害是否成立」的判定，完全不打断原版伤害链路（护甲 / 药水 / 附魔保护
 * 该怎么算还怎么算）；致死伤直接跳过 —— 尸体与掉落物处理归掉落系统管，我们不抢。
 *
 * <p><b>兼容第三方 mod</b>：换位对象是「现场收集附近实体」，不预建名单 ——
 * 任何 mod 加的生物只要在附近且是 {@link LivingEntity} 就可能被选中；
 * 想挡掉的（mod Boss 等）用配置黑名单按命名空间 / 精确 id 排除。
 *
 * <p><b>设计底线对照</b>：只在服务端判定（Fabric 事件，无客户端代码）✓；
 * 零持久化（无任何状态，关服即清零）✓；不碰物品（位置互换无掉落）✓。
 */
public final class HurtSwap {

	/** 「附近没有可交换对象」提示的限频间隔（5 秒）。 */
	private static final long HINT_INTERVAL_TICKS = 100L;
	/** 限频表（内存态，零持久化 —— 关服即清）。 */
	private static final java.util.Map<java.util.UUID, Long> LAST_HINT_AT = new java.util.HashMap<>();

	/**
	 * 公屏播报模板（随机轮换），{@code {player}} 换成触发玩家、{@code {target}} 换成换位对象。
	 * 加新句子直接往里塞一行 —— 播报是喜剧效果的核心，别让文案变得一本正经。
	 */
	private static final List<String> BROADCAST_TEMPLATES = List.of(
			"{player} 与 {target} 互换了人生",
			"{player} 与 {target} 互换了位置",
			"{player} 与 {target} 互换了空间",
			"{player} 与 {target} 交换了生活",
			"{player} 与 {target} 互换了命运");

	private HurtSwap() {
	}

	/** 关服清理（包入口在 SERVER_STOPPING 调用）。 */
	public static void clearTransientState() {
		LAST_HINT_AT.clear();
	}

	/** 接入 Fabric 伤害事件（各包入口在 onInitialize 里调用）。 */
	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HurtSwap::onDamage);
	}

	private static void onDamage(LivingEntity entity, DamageSource source, float amount,
			float newHealth, boolean blocked) {
		SwapConfig config = SwapConfig.get();

		// 「掉血」= 伤害真的成立了：被格挡 / 零伤害 / 已经死了（死亡流程归掉落系统）都不算
		if (!config.hurtSwapEnabled || blocked || amount <= 0.0f) {
			return;
		}
		if (newHealth <= 0.0f || !entity.isAlive() || entity.isSpectator()) {
			return;
		}
		// 触发点只有一个：玩家掉血。生物受伤不触发、生物之间不互换
		if (!(entity instanceof ServerPlayer)) {
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LivingEntity partner = pickPartner(level, entity, config);
		if (partner != null) {
			applySwap(level, entity, partner, config);
		} else {
			// 不换也得吱一声 —— 「明明掉了血却没反应」比换位本身更让人困惑（溺水报告的教训）
			hintNoPartner(entity, level, config);
		}
	}

	/** 附近没有可交换对象时的提示，按玩家限频（5 秒一条），防止溺水每秒掉血刷屏。 */
	private static void hintNoPartner(LivingEntity entity, ServerLevel level, SwapConfig config) {
		if (!config.hurtSwapAnnounce || !(entity instanceof ServerPlayer sp)) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_HINT_AT.get(sp.getUUID());
		if (last != null && now - last < HINT_INTERVAL_TICKS) {
			return;
		}
		LAST_HINT_AT.put(sp.getUUID(), now);
		sp.sendSystemMessage(Component.literal(
				"§7[随机换位] " + (int) config.hurtSwapMaxRadius + " 格内没有可交换的对象"));
	}

	/**
	 * 在受伤者附近找一个随机换位对象，没有合格的返回 null。
	 *
	 * <p>排除项（每条都对应一个真实的坑）：自身 / 已死 / 盔甲架（装饰品换过去没意义）/
	 * 旁观者 / 骑乘状态 / 黑名单（Boss 换位等于送人头）/ 站在岩浆或火里的（换过去即死，
	 * 「爽但不劝退」）。玩家由 {@code hurtSwapIncludePlayers} 单独控制 ——
	 * 有些服务器只想要生物间的混乱，不想 PvP 被随机化。
	 */
	static LivingEntity pickPartner(ServerLevel level, LivingEntity hurt, SwapConfig config) {
		Vec3 center = hurt.position();
		AABB box = new AABB(center, center).inflate(config.hurtSwapMaxRadius);

		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> isEligible(level, e, hurt, config));

		return candidates.isEmpty() ? null : candidates.get(level.getRandom().nextInt(candidates.size()));
	}

	private static boolean isEligible(ServerLevel level, LivingEntity e, LivingEntity hurt, SwapConfig config) {
		if (e == hurt || !e.isAlive() || e.isSpectator()) {
			return false;
		}
		if (e instanceof ArmorStand) {
			return false;
		}
		if (!config.hurtSwapIncludePlayers && e instanceof ServerPlayer) {
			return false;
		}
		if (e.isPassenger() || e.isVehicle()) {
			return false;
		}
		if (config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) {
			return false;
		}
		return !isHazard(level, e.position());
	}

	/** 落点危险检查：脚底或头部一格是岩浆 / 火，换过去就是白给，跳过。 */
	private static boolean isHazard(ServerLevel level, Vec3 pos) {
		BlockPos feet = BlockPos.containing(pos);
		return level.getBlockState(feet).is(Blocks.LAVA)
				|| level.getBlockState(feet).is(Blocks.FIRE)
				|| level.getBlockState(feet.above()).is(Blocks.LAVA);
	}

	/**
	 * 执行互换：传送双方、清摔落距离、播末影人音效、给双方玩家发提示。
	 * 包内可见 —— 自检直接调用它做位置互换断言。
	 */
	static void applySwap(ServerLevel level, LivingEntity a, LivingEntity b, SwapConfig config) {
		Vec3 pa = a.position();
		Vec3 pb = b.position();

		// 26.2 的 Entity.teleportTo(x,y,z) 对玩家也会发位置同步包（setPos 只改服务端状态）
		a.teleportTo(pb.x, pb.y, pb.z);
		b.teleportTo(pa.x, pa.y, pa.z);

		if (config.hurtSwapClearFallDistance) {
			// 对方攒的摔落不该由我买单（换到悬崖边瞬间摔死 = 劝退）
			a.resetFallDistance();
			b.resetFallDistance();
		}

		if (config.hurtSwapAnnounce) {
			level.playSound(null, pa.x, pa.y, pa.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
			level.playSound(null, pb.x, pb.y, pb.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

			// 公屏播报：模板随机轮换，占位符替换后发给全服（不带任何前缀）
			String template = BROADCAST_TEMPLATES.get(
					level.getRandom().nextInt(BROADCAST_TEMPLATES.size()));
			String message = template
					.replace("{player}", "§f" + a.getDisplayName().getString() + "§b")
					.replace("{target}", "§f" + b.getDisplayName().getString() + "§b");
			level.getServer().getPlayerList().broadcastSystemMessage(
					Component.literal("§b" + message), false);
		}

		if (SwapConfig.get().debugLog) {
			Yg.LOGGER.info("[yg-swap] {} ⇄ {} 互换位置（{} ⇄ {}）",
					a.getName().getString(), b.getName().getString(),
					format(pa), format(pb));
		}
	}

	private static String format(Vec3 v) {
		return String.format(java.util.Locale.ROOT, "%.0f,%.0f,%.0f", v.x, v.y, v.z);
	}

	/** 自检专用：按注册表 id 造一只活体（绕开「26.2 动物类包结构变动大」的导入风险）。 */
	static LivingEntity spawnById(ServerLevel level, String id, Vec3 pos) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
				net.minecraft.resources.Identifier.parse(id));
		if (type == null) {
			return null;
		}

		net.minecraft.world.entity.Entity entity = type.create(level, EntitySpawnReason.COMMAND);
		if (entity == null || !(entity instanceof LivingEntity living)) {
			return null;
		}

		living.setPos(pos.x, pos.y, pos.z);
		level.addFreshEntity(living);
		return living;
	}
}
