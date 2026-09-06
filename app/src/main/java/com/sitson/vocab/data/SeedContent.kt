package com.sitson.vocab.data

import org.json.JSONArray
import org.json.JSONObject

data class SeedSense(val word: ImportedWord, val contexts: List<SeedContext>)
data class SeedContext(val sentence: String, val translation: String, val family: String, val wrong: List<String>,
    val productionPrompt: String = "", val productionAnswer: String = "", val alternatives: List<String> = emptyList())

/** Bundled, authored examples. Families describe different uses/scenarios, not merely different strings. */
object SeedContent {
    val senses = listOf(
        SeedSense(ImportedWord("abandon", "/əˈbændən/", "放弃；不再继续", "abandon the plan", "They decided to abandon the plan."), listOf(
            SeedContext("They decided to abandon the plan after repeated failures.", "多次失败后，他们决定放弃这个计划。", "planning", listOf("他们决定加快计划。", "他们决定重复原计划。"), alternatives = listOf("give up", "drop")),
            SeedContext("The crew had to abandon the ship during the storm.", "风暴中，船员不得不弃船。", "emergency", listOf("船员在风暴中修理船只。", "船员在风暴中加速航行。")),
        )),
        SeedSense(ImportedWord("charge", "/tʃɑːrdʒ/", "收取费用", "charge extra", "Some hotels charge extra for breakfast."), listOf(
            SeedContext("Some hotels charge extra for breakfast.", "有些酒店的早餐需要额外付费。", "hotel", listOf("有些酒店免费提供早餐。", "有些酒店取消了早餐。")),
            SeedContext("These shops charge a small fee for delivery.", "这些商店收取少量配送费。", "shopping", listOf("这些商店拒绝送货。", "这些商店免费送货。")),
        )),
        SeedSense(ImportedWord("charge", "/tʃɑːrdʒ/", "正式指控", "charge someone with a crime", "The police may charge him with theft."), listOf(
            SeedContext("The police may charge him with theft.", "警方可能以盗窃罪指控他。", "police", listOf("警方可能向他收取费用。", "警方可能把失物交给他。")),
            SeedContext("Prosecutors plan to charge the director with fraud.", "检察官计划指控这位主管涉嫌欺诈。", "court", listOf("检察官计划聘请这位主管。", "检察官计划向这位主管付费。")),
        )),
        SeedSense(ImportedWord("depend", "/dɪˈpend/", "依赖；取决于", "depend on", "Our plans depend on the weather."), listOf(
            SeedContext("Our plans depend on the weather.", "我们的计划取决于天气。", "planning", listOf("我们的计划会改变天气。", "我们的计划与天气无关。"), "Our plans depend ____ the weather.", "on"),
            SeedContext("Young children depend on adults for food and shelter.", "年幼的孩子依赖成年人提供食物与住所。", "care", listOf("年幼的孩子为成年人提供住所。", "年幼的孩子拒绝成年人的帮助。"), alternatives = listOf("rely")),
        )),
        SeedSense(ImportedWord("maintain", "/meɪnˈteɪn/", "保持；维持", "maintain a steady speed", "Drivers should maintain a steady speed."), listOf(
            SeedContext("Drivers should maintain a steady speed on this road.", "驾驶员在这条路上应保持稳定车速。", "driving", listOf("驾驶员在这条路上应不断加速。", "驾驶员在这条路上应立即停车。"), alternatives = listOf("keep")),
            SeedContext("We need to maintain a comfortable temperature in the room.", "我们需要使房间保持舒适的温度。", "room", listOf("我们需要使房间温度持续升高。", "我们需要使房间尽可能寒冷。"), alternatives = listOf("keep")),
        )),
        SeedSense(ImportedWord("subtle", "/ˈsʌtəl/", "细微而不易察觉的", "a subtle difference", "There is a subtle difference between these colors."), listOf(
            SeedContext("There is a subtle difference between these colors.", "这些颜色之间有不易察觉的细微差别。", "colors", listOf("这些颜色之间的差别非常明显。", "这些颜色完全相同。")),
            SeedContext("Her subtle hint helped me understand what she wanted.", "她不明显的暗示帮助我理解了她的想法。", "conversation", listOf("她大声命令我照她说的做。", "她的说明非常直接明确。")),
        )),
    )
    fun exercise(context: SeedContext) = JSONObject().apply {
        put("corePattern", true)
        put("choices", JSONArray(listOf(context.translation) + context.wrong)); put("choiceAnswer", context.translation)
        put("productionPrompt", context.productionPrompt); put("productionAnswer", context.productionAnswer)
        put("alternatives", JSONArray(context.alternatives))
    }.toString()
}
