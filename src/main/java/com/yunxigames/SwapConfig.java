package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 随机换位（yunxigames swap 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-swap.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。
 *
 * <p><b>默认值原则「爽但不劝退」</b>：受伤换位很混沌，概率默认压在 15% ——
 * 一场战斗大概换一两次，够刺激又不至于打怪全靠信仰。
 */
public final class SwapConfig extends YgConfig {
	public static final String FILE_NAME = "yg-swap.json";

	// ---------- 受伤随机换位 ----------

	/** 总开关：玩家掉血就直接与附近随机一个活体（生物或其他玩家）互换位置。 */
	public boolean hurtSwapEnabled = true;

	/** 换位对象的搜索半径（格，默认 48：一般战斗场景里「附近」的合理范围）。 */
	public double hurtSwapMaxRadius = 48.0;

	/** 换位对象是否包含其他玩家（默认开：换位池 = 附近的生物 + 玩家）。 */
	public boolean hurtSwapIncludePlayers = true;

	/** 换位后清空双方摔落距离（换到悬崖边不会因为对方攒的摔落白送摔死 —— 爽但不劝退）。 */
	public boolean hurtSwapClearFallDistance = true;

	/** 换位发生时给双方玩家发提示 + 播末影人音效（反馈演出）。 */
	public boolean hurtSwapAnnounce = true;

	/**
	 * 生物黑名单：这些 id 不会被选为换位对象（Boss 换位等于传送到死）。
	 * 支持精确 id 与 {@code 命名空间:*} 通配 —— 第三方 mod 的 Boss 也可以在这里挡掉。
	 */
	public List<String> entityBlacklist = new ArrayList<>(List.of(
			"minecraft:ender_dragon", "minecraft:wither"));

	private transient YgConfig.IdFilter entityBlacklistFilter = YgConfig.IdFilter.EMPTY;

	public boolean isEntityBlacklisted(net.minecraft.resources.Identifier id) {
		return entityBlacklistFilter.matches(id);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-swap.json");
	private static volatile SwapConfig instance;

	SwapConfig() {  // 包内可见：单元测试与 YgConfig 缺项补回需要 new 默认实例
	}

	/** 自检专用：造一份全新默认配置（绕开单例，不落盘、不影响运行中的 instance）。 */
	static SwapConfig blankForTest() {
		return new SwapConfig();
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static SwapConfig get() {
		SwapConfig local = instance;
		if (local == null) {
			synchronized (SwapConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized SwapConfig load() {
		Path path = configPath(FILE_NAME);
		SwapConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 时用它区分「json 里没写这一项」和「明确写了 false」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, SwapConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-swap.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new SwapConfig();
		} else {
			mergeMissingFields(loaded, raw, new SwapConfig());
		}

		loaded.validate();
		instance = loaded;
		loaded.save();
		return loaded;
	}

	/** 把当前配置写回磁盘。 */
	public synchronized void save() {
		Path path = configPath(FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("[yg-swap.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析黑名单过滤器（NaN 一并治：!(x>=lo && x<=hi) 对 NaN 恒真）。 */
	void validate() {
		if (entityBlacklist == null) entityBlacklist = new ArrayList<>();
		entityBlacklistFilter = parseFilter(entityBlacklist, "entityBlacklist");

		if (!(hurtSwapMaxRadius >= 8.0 && hurtSwapMaxRadius <= 256.0)) hurtSwapMaxRadius = 48.0;
	}
}
