namespace YMM4Craft.Models;

internal static class BlockStateNames
{
    static readonly Dictionary<string, string> Properties = new()
    {
        ["facing"] = "向き",
        ["horizontal_facing"] = "向き",
        ["axis"] = "軸",
        ["rotation"] = "回転",
        ["half"] = "上下",
        ["type"] = "形",
        ["shape"] = "形",
        ["hinge"] = "ちょうつがい",
        ["open"] = "開く",
        ["powered"] = "動力",
        ["lit"] = "点灯",
        ["waterlogged"] = "水浸し",
        ["age"] = "成長",
        ["level"] = "レベル",
        ["face"] = "付ける面",
        ["attachment"] = "付け方",
        ["part"] = "部分",
        ["occupied"] = "使用中",
        ["hanging"] = "吊り下げ",
        ["snowy"] = "雪",
        ["north"] = "北",
        ["south"] = "南",
        ["east"] = "東",
        ["west"] = "西",
        ["up"] = "上",
        ["down"] = "下",
    };

    static readonly Dictionary<string, string> Values = new()
    {
        ["north"] = "北",
        ["south"] = "南",
        ["east"] = "東",
        ["west"] = "西",
        ["up"] = "上",
        ["down"] = "下",
        ["top"] = "上",
        ["bottom"] = "下",
        ["upper"] = "上",
        ["lower"] = "下",
        ["double"] = "2段",
        ["left"] = "左",
        ["right"] = "右",
        ["head"] = "頭",
        ["foot"] = "足",
        ["floor"] = "床",
        ["wall"] = "壁",
        ["ceiling"] = "天井",
        ["straight"] = "まっすぐ",
        ["inner_left"] = "内角（左）",
        ["inner_right"] = "内角（右）",
        ["outer_left"] = "外角（左）",
        ["outer_right"] = "外角（右）",
        ["true"] = "オン",
        ["false"] = "オフ",
        ["none"] = "なし",
        ["low"] = "低い",
        ["tall"] = "高い",
        ["side"] = "横",
    };

    public static string Property(string name) => Properties.TryGetValue(name, out var label) ? $"{label}（{name}）" : name;

    public static string Value(string value) => Values.TryGetValue(value, out var label) ? $"{label}（{value}）" : value;

    public static Dictionary<string, string> Parse(string text)
    {
        var map = new Dictionary<string, string>();
        foreach (var pair in text.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var eq = pair.IndexOf('=');
            if (eq > 0) map[pair[..eq]] = pair[(eq + 1)..];
        }
        return map;
    }

    public static string Format(IEnumerable<KeyValuePair<string, string>> pairs) => string.Join(",", pairs.Select(p => $"{p.Key}={p.Value}"));
}
