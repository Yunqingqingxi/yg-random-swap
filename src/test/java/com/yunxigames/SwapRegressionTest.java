package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-swap 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 * 本包从诞生起就用 JsonObject 存在性检查，这里保证它不退化。
 */
class SwapRegressionTest {

	@TempDir
	Path configDir;

	@BeforeEach
	void injectConfigDir() {
		YgConfig.configDirOverride = configDir;
	}

	@AfterEach
	void resetConfigDir() {
		YgConfig.configDirOverride = null;
	}

	@Test
	void missingBooleanFieldsFallBackToCodeDefaultTrue() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME),
				"{\"hurtSwapEnabled\": true}");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapIncludePlayers, "缺项布尔必须补回代码默认 true");
		assertTrue(cfg.hurtSwapAnnounce, "缺项布尔必须补回代码默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME),
				"{\"hurtSwapEnabled\": true, \"hurtSwapIncludePlayers\": false}");
		SwapConfig cfg = SwapConfig.load();
		assertFalse(cfg.hurtSwapIncludePlayers, "玩家明确写 false 必须保持 false");
	}

	@Test
	void corruptedConfigFallsBackToDefaults() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME), "{{{不是json");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapEnabled, "损坏文件应回退到默认配置而不是崩溃");
		assertFalse(cfg.entityBlacklist.isEmpty(), "默认黑名单（龙/凋灵）应就位");
	}

	@Test
	void legacyConfigWithRemovedFieldsStillLoads() throws Exception {
		// 1.0.x 的老配置里有 hurtSwapChance / hurtSwapMobsCanTrigger（2.0.0 已删）——
		// Gson 对未知字段天然忽略，load 必须成功且落到新口径（掉血直接换）
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME),
				"{\"hurtSwapEnabled\": true, \"hurtSwapChance\": 0.15, \"hurtSwapMobsCanTrigger\": true}");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapEnabled, "老配置必须能读，不能崩");
		assertTrue(cfg.hurtSwapIncludePlayers, "换位池默认含玩家");
	}

	@Test
	void newFieldsMissingInLegacyConfigTakeNewDefaults() throws Exception {
		// 1.0.0 的老配置缺 2.0.0 的字段 —— 缺项必须补新默认
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME), "{\"hurtSwapEnabled\": true}");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapIncludePlayers, "缺项布尔补 2.0.0 默认 true");
		assertEquals(48.0D, cfg.hurtSwapMaxRadius, "缺项数值补 2.0.0 默认 48");
	}
}
