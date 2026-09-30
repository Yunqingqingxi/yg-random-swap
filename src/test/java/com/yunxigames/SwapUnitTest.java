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
	void radiusBounds() {
		SwapConfig cfg = new SwapConfig();
		cfg.hurtSwapMaxRadius = 3.0D;
		cfg.validate();
		assertEquals(48.0D, cfg.hurtSwapMaxRadius, "半径低于 8 回落默认 48");

		cfg.hurtSwapMaxRadius = 1000.0D;
		cfg.validate();
		assertEquals(48.0D, cfg.hurtSwapMaxRadius, "半径超过 256 回落默认");
	}

	@Test
	void legalRadiusPassesThroughUnchanged() {
		SwapConfig cfg = new SwapConfig();
		cfg.hurtSwapMaxRadius = 128.0D;
		cfg.validate();
		assertEquals(128.0D, cfg.hurtSwapMaxRadius);
	}

	@Test
	void defaultsAreTheKeepItSimpleEdition() {
		SwapConfig cfg = new SwapConfig();
		assertEquals(48.0D, cfg.hurtSwapMaxRadius);
		assertTrue(cfg.hurtSwapIncludePlayers, "换位池 = 附近生物 + 玩家");
		assertTrue(cfg.hurtSwapEnabled, "总开关默认开");
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
