# QC3 · 海外地图 Sug Query 纠错

面向海外多国家、多语言地图 **Sug（联想）** 场景的 query 纠错引擎：修拼写错误、保留合法未完成前缀、**不做补全**（补全交给 Sug），目标端到端延迟约 **60ms**。

默认内置墨西哥 / 英国 / 马来西亚 / 印尼的合成样例数据，开箱可跑；线上可对接 `poi_correcton_v2`、`poi_geo_abroad_v2` 与 Google top-1 查询日志。

## 示例

| 输入 | 输出 | 说明 |
|---|---|---|
| `aple` | `appl` | 前缀纠错，不补全成 `apple` |
| `starbucks coefe` | `starbucks coffe` | 末词未打完，保留合法前缀 |
| `Ecatepec de Moaalos` | `Ecatepec de Morelos` | 多词 + 上下文 |
| `Starbucls coff` | `Starbucks coff` | 修前面的词，末词不动 |
| `The Toeer of London` | `The Tower of London` | bigram 上下文消歧 |

## 模块

| 模块 | 说明 |
|---|---|
| `qc-core` | 词典 / 模糊召回 / 通道模型 / 点击挖掘 / 格解码 / 训练评估 / ES 导出 / 离线 CLI |
| `qc-service` | Spring Boot HTTP 服务 + 调试台（默认端口 **18761**） |

技术栈：Java 17（JDK 21 可编译）、Maven、Spring Boot 3.3、Jackson、commons-codec（Double Metaphone）。

## 快速开始

```bash
# 构建
mvn -q package

# 启动服务（默认 SAMPLE 合成数据，无需 ES）
java -Xmx2g -jar qc-service/target/qc-service.jar

# 调试台
open http://127.0.0.1:18761/

# 纠错 API
curl 'http://127.0.0.1:18761/api/v1/correct?q=Ecatepec%20de%20Moaalos&country=484'
```

离线一键演示（消融 + 感知机训练 + 评估）：

```bash
java -jar qc-core/target/qc-core-*-cli.jar demo --out data/sample
```

## HTTP API

### `GET /api/v1/correct`

| 参数 | 必填 | 说明 |
|---|---|---|
| `q` | ✓ | 原始 query |
| `country` | ✓ | 国家 ID（ISO numeric，如 MX=484, GB=826, MY=458, ID=360） |
| `city` | | 城市 ID，用于城市频次特征；默认 0 |
| `incomplete` | | 覆盖「末词是否未完成」；默认按末字符是否为空格推断 |
| `debug` | | `true` 时返回候选格调试信息 |

### `POST /api/v1/correct`

```json
{
  "query": "The Toeer of London",
  "countryId": 826,
  "cityId": 8260001,
  "lastTokenIncomplete": false,
  "debug": false
}
```

### `POST /api/v1/correct/batch`

请求体为上述对象的数组。

### 其它

- `GET /api/v1/meta` — 已加载国家、词典规模、模型版本
- `POST /admin/reload` — 热加载产物（原子替换，不影响在途请求）
- `GET /actuator/health` — 健康检查

### 响应字段（要点）

```json
{
  "original": "Starbucls coff",
  "corrected": "Starbucks coff",
  "changed": true,
  "confidence": 0.99,
  "level": "HIGH",
  "corrections": [
    {
      "from": "Starbucls",
      "to": "Starbucks",
      "level": "HIGH",
      "applied": true,
      "edits": 1,
      "sources": ["TRIE", "ERROR_DICT"]
    }
  ],
  "latencyMicros": 248,
  "skipReason": null
}
```

`level`：`NONE` / `LOW` / `MEDIUM` / `HIGH`。仅 **MEDIUM+HIGH** 会改写输出；LOW 表示有提议但置信度不够（fail-closed 倾向）。

内部异常时 **fail-open**：返回原文，`skipReason=internal_error`。

## Sug 接入建议

1. Sug 收到输入后先调纠错，再用 `corrected` 做联想；超时（建议 ≤40–50ms）则用原文。
2. 传入 `country`（必填）和当前城市 `city`（有则传）。
3. 末词是否打完：query 以空格结尾 → 末词已完成；否则末词按前缀模式纠错（`aple`→`appl`，不会补成完整词）。
4. 只在 `level` 为 MEDIUM/HIGH 时采用结果；也可自行按 `confidence` 卡阈值。
5. 国家未开通 / query 过短 / token 过多时引擎直接透传原文。

嵌入式调用（不经 HTTP）可直接依赖 `qc-core`：

```java
QueryCorrector qc = ...; // CorrectorHolder 或 Artifacts.load(...)
CorrectionResult r = qc.correct(CorrectionRequest.of(query, countryId, cityId));
String sugQuery = r.changed() ? r.corrected() : query;
```

## 配置（`qc-service`）

`qc-service/src/main/resources/application.yml`：

```yaml
server.port: 18761

qc:
  # SAMPLE | FILES | ES
  source: SAMPLE
  data-dir: data/artifacts
  model-file: ""                 # 空则用 data-dir/model.json 或内置 sample-model
  country-whitelist: []          # 空 = 已加载的全部国家
  judge-enabled: true            # 高频词快速判定「已正确」
  deadline-micros: 40000         # 引擎预算；超时 fail-open 返回原文
  es:
    url: http://localhost:9200
    user: ""
    password: ""
    lexicon-index: poi_correcton_v2
    countries: [484, 826, 458, 360]
```

| `source` | 行为 |
|---|---|
| `SAMPLE` | 启动时生成合成 MX/GB/MY/ID 世界（演示 / 单测） |
| `FILES` | 从 `data-dir` 加载离线产物 |
| `ES` | 词典从 ES `poi_correcton_v2` 拉；bigram / errdict / channel / model 仍从 `data-dir` 读 |

## 离线 CLI

```bash
CLI=qc-core/target/qc-core-1.0.0-SNAPSHOT-cli.jar
```

| 命令 | 作用 |
|---|---|
| `demo` | 合成数据 → 消融 → 感知机训练 → 评估 → 写 `data/sample` |
| `gen-sample` | 只写出合成 `pois` / `clicks` / `test` TSV |
| `build` | POI + 点击日志 (+ 规则挖掘 pair) → 产物目录 |
| `train` | 在产物上训练 `model.json` |
| `eval` | 评估 labeled TSV |
| `correct` | 单条纠错（debug JSON） |
| `es-lexicon` | 导出 `poi_correcton_v2` → `lexicon.tsv.gz` |
| `es-pois` | 导出 `poi_geo_abroad_v2` 名称 → `pois.tsv.gz`（建 bigram） |
| `bench` | 把每国词典膨胀到生产量级测延迟 |

### 接真实数据的推荐流程

```bash
# 1) 从 ES 导出词典与 POI 文本
java -jar $CLI es-lexicon --es http://es:9200 --user U --password P \
  --countries 484,826,458,360 --out data/input/lexicon.tsv.gz

java -jar $CLI es-pois --es http://es:9200 --user U --password P \
  --countries 484,826,458,360 --out data/input/pois.tsv.gz

# 2) 准备点击日志（TSV，可 .gz）
#    countryId  cityId  uid  query  googleName  googleAddress

# 3) （可选）已有规则挖掘 pair
#    countryId  error  correction  count
#    例：826  nyrnber  nurnber  1

# 4) 构建产物
java -jar $CLI build \
  --pois data/input/pois.tsv.gz \
  --clicks data/input/clicks.tsv.gz \
  --pairs data/input/pairs.tsv \
  --lexicon data/input/lexicon.tsv.gz \
  --out data/artifacts

# 5) 训练 / 评估
java -jar $CLI train --dir data/artifacts
java -jar $CLI eval  --dir data/artifacts --test data/input/test.tsv

# 6) 服务改用 FILES
#    qc.source=FILES  qc.data-dir=data/artifacts
```

### TSV 格式

```
pois     poiId  countryId  cityId  name  address  postcode(0|1)
clicks   countryId  cityId  uid  query  googleName  googleAddress
labeled  countryId  cityId  query  expected          # query 末尾空格 = 末词已完成
pairs    countryId  error  correction  count
```

### 产物目录

```
lexicon.tsv.gz   # 词典（或 ES 快照）
bigram.tsv.gz    # 上下文二元模型
errdict.tsv      # 点击挖掘错误词典 P(corr|err)
channel.tsv      # 字符编辑统计
model.json       # 排序权重 + 校准 + 阈值
```

## 方案要点（简述）

1. **噪声信道 + 上下文格解码**：每词候选集合恒含「保持原文」；Viterbi 在 log-linear 特征上解码；前后向 max-marginal 给出置信度。
2. **前缀感知**：末词未完成时走前缀模式；`PrefixAlign` 把目标词截成与输入对齐的合法前缀（`aple`→`appl`，`coefe`→`coffe`），保证「只纠错不补全」。
3. **召回**：CompactTrie + 有界 OSA、Double Metaphone、trigram（长词）、点击错误词典、分合词。
4. **点击 / Google top-1**：对齐 query 与 Google 结果 → 错误词典、通道计数、训练标签；也可并入已有规则 pair（如 `nyrnber`→`nurnber`）。
5. **上下文**：按国 bigram（POI 名址 + Google 名加权），用平滑 PMI 做边特征——补齐 `poi_correcton_v2` 无上下文的缺口。
6. **训练**：结构化感知机 + 留出集 logistic 校准；阈值按目标 precision（默认 0.92）切 MEDIUM/HIGH。

更完整的设计说明、v1（~52% 正确纠错率）问题分析、上线与评估指标见计划中的 `docs/DESIGN.md`（若尚未合入，以本 README 与源码包注释为准）。

## 合成数据上的参考指标

在内置合成世界（非线上网数据）上，`demo` / `eval` 量级大致为：

| 配置 | Precision | Recall | 误纠率 |
|---|---|---|---|
| 仅 token | ~97.8% | ~94.5% | ~0.6% |
| + bigram | ~98.7% | ~97.3% | — |
| + 点击词典/通道 + 训练 | **~99%** | **~98%** | **~0.15%** |

延迟（合成词典）：p50 ~0.1ms；膨胀到约 30 万额外 token/国后 p50 ~0.4ms、p99 数毫秒。  
**这些数字不能直接与线上 v1 的 ~52% 对比**；接真实 ES / 日志后请用 `eval` 与影子流量重测。

## 测试

```bash
mvn test
```

覆盖分词与前缀对齐、模糊检索、点击挖掘（含 `nyrnber`→`nurnber`）、ES loader mock、端到端用户示例，以及 MockMvc REST 用例。

## 目录结构

```
pom.xml
qc-core/          # 引擎与离线工具
qc-service/       # HTTP 服务 + static 调试台
```

## License

内部项目，按公司规范使用。
