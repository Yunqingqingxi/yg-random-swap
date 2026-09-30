package com.yunxigames;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-swap 单元测试：validate() 钳制与黑名单过滤的纯逻辑验证。
 */
class SwapUnitTest {

	@Test
	void nanChanceFallsBackToDefault() {
		SwapConfig cfg = new SwapConfig();
		cfg.hurtSwapChance = Float.NaN;
		cfg.validate();
		assertEquals(1.0F, cfg.hurtSwapChance, "NaN 概率必须回落默认 1.0（必定换位）");
	}

	@Test
	void chanceAndRadiusBounds() {
		SwapConfig cfg = new SwapConfig();
		cfg.hurtSwapChance = 1.5F;
		cfg.hurtSwapMaxRadius = 3.0D;
		cfg.validate();
		assertEquals(1.0F, cfg.hurtSwapChance, "概率超过 1 回落默认");
		assertEquals(48.0D, cfg.hurtSwapMaxRadius, "半径低于 8 回落默认 48");

		cfg.hurtSwapMaxRadius = 1000.0D;
		cfg.validate();
		assertEquals(48.0D, cfg.hurtSwapMaxRadius, "半径超过 256 回落默认");
	}

	@Test
	void legalValuesPassThroughUnchanged() {
		SwapConfig cfg = new SwapConfig();
		cfg.hurtSwapChance = 0.3F;
		cfg.hurtSwapMaxRadius = 128.0D;
		cfg.validate();
		assertEquals(0.3F, cfg.hurtSwapChance, "调小概率是合法值（必定换位可调）");
		assertEquals(128.0D, cfg.hurtSwapMaxRadius);
	}

	@Test
	void defaultsAreThePlayerOnlyEdition() {
		SwapConfig cfg = new SwapConfig();
		assertEquals(1.0F, cfg.hurtSwapChance, "1.0.1 定版：必定换位");
		assertTrue(cfg.hurtSwapIncludePlayers, "换位池 = 附近生物 + 玩家");
		assertFalse(cfg.hurtSwapMobsCanTrigger, "生物受伤不触发（玩家单向触发）");
	}

	@Test
	void blacklistSupportsNamespaceWildcard() {
		SwapConfig cfg = new SwapConfig();
		cfg.entityBlacklist = new ArrayList<>(List.of("somemod:*", "minecraft:ender_dragon"));
		cfg.validate();

		assertTrue(cfg.isEntityBlacklisted(Identifier.parse("somemod:boss")),
				"第三方 Boss 用命名空间通配拦下（兼容约定）");
		assertFalse(cfg.isEntityBlacklisted(Identifier.parse("othermod:boss")));
		assertTrue(cfg.isEntityBlacklisted(Identifier.parse("minecraft:ender_dragon")));
		assertFalse(cfg.isEntityBlacklisted(Identifier.parse("minecraft:zombie")));
	}
}
