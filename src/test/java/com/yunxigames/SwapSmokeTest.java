package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-swap 冒烟测试：mod 描述文件合法、配置能从零生成并写回、改动能落盘再读回。
 */
@Tag("smoke")
class SwapSmokeTest {

	private static final Gson GSON = new Gson();

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
	void fabricModJsonIsValidWithCorrectModId() throws Exception {
		try (var in = getClass().getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json 必须在 jar 资源里");
			JsonObject json = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("yg_swap", json.get("id").getAsString());
			assertEquals(1, json.get("schemaVersion").getAsInt());
			assertNotNull(json.get("entrypoints").getAsJsonObject().get("main"));
		}
	}

	@Test
	void loadCreatesDefaultConfigFileOnDisk() {
		SwapConfig cfg = SwapConfig.load();
		assertTrue(Files.isRegularFile(configDir.resolve(SwapConfig.FILE_NAME)),
				"load() 后配置文件必须已写回磁盘");
		assertTrue(cfg.hurtSwapEnabled, "受伤换位默认开");
		assertTrue(cfg.hurtSwapIncludePlayers, "换位池包含玩家（云兮定的玩法口径）");
	}

	@Test
	void modifiedValuesSurviveSaveLoadRoundtrip() {
		SwapConfig cfg = SwapConfig.load();
		cfg.hurtSwapMaxRadius = 96.0D;
		cfg.hurtSwapAnnounce = false;
		cfg.save();

		SwapConfig reloaded = SwapConfig.load();
		assertEquals(96.0D, reloaded.hurtSwapMaxRadius);
		assertFalse(reloaded.hurtSwapAnnounce, "写盘的 false 必须原样读回");
	}
}
