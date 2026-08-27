我认为不应该先从“题型”设计，而应该从一个问题开始：

> 每一次用户交互，究竟想获得什么学习效果，或者想测量什么能力？

这样推导下来，一个 AI 背单词系统真正需要的不是几十种花哨小游戏，而是大约 **6 类基础交互原语**。不同学习阶段只是把它们组合起来。

1. **接触 / 建模（Encounter）**  
第一次见一个词时，不应该立刻考试，也不应该扔给用户一大页词典解释。目标是建立最初的“概念—形式—语境”连接。

例如学习 `subtle`：

> There is a subtle difference between the two results.  
> 两个结果之间存在一种不容易察觉的细微差别。

然后突出 `subtle`，给出非常短的信息：

> subtle：细微而不易察觉的  
> 常见：subtle difference / subtle change / subtle hint

这里用户不需要做什么复杂操作。最多可以让他点一下：

> “懂了” / “还是不清楚”

AI 的作用是根据“不清楚”继续换一个更容易理解的例子，而不是把同一句解释重复一次。

这里的学习目标是：

\[
\text{meaning} \leftrightarrow \text{word}
\]

建立最初连接。

---

2. **语境理解（Comprehension）**

这是检验：

> 当词出现在一个以前没见过的句子里，我能不能理解？

例如：

> The medicine produced a subtle improvement in his condition.

不要简单问：

> subtle 是什么意思？

可以问：

> 这句话更接近哪一种？  
> A. 病情明显改善  
> B. 病情有轻微、不易察觉的改善  
> C. 病情突然恶化

这种模式的重点是：**理解整个语境中的词义，而不是翻译单词。**

AI 每次都可以生成新语料。

而且这里应该允许一种非常重要的回答：

> “我理解整句话，但说不出 subtle 的精确中文意思。”

这其实可能算成功。

因为真实阅读能力并不要求每个英语词都能翻译成中文。

---

3. **主动提取（Production / Recall）**

这是整个系统里最重要的模式之一。

不展示目标词：

> “这个变化非常细微，很难注意到。”  
> The change was very ______.

用户输入：

> subtle

或者进一步开放：

> 用你最近学过的词，把这句话表达得更自然：  
> “There was a small difference, but it was difficult to notice.”

这里 AI 不应该只进行字符串判定。

例如用户写：

> There was a slight difference.

这是正确英语，但没有使用目标词。

系统应该反馈：

> 这句话正确，`slight` 也很自然。  
> 不过我们现在训练的是 `subtle`。  
> `slight` 更强调程度小，`subtle` 更强调不明显、难察觉。

这样一次“答错”反而成为一次更有价值的词义边界学习。

---

4. **辨析 / 选择（Discrimination）**

这是传统背单词软件严重不足的一类。

现实语言使用不是：

> “我是否知道 subtle？”

而经常是：

> “这里到底该用 subtle、slight 还是 minor？”

例如：

> There is a ___ difference in tone between the two versions.

提供：

> subtle / slight / tiny

但问题不是答完就结束。

真正重要的是 AI 追问：

> 为什么 `subtle` 在这里比 `tiny` 更自然？

或者直接展示几个上下文：

> subtle hint  
> slight headache  
> minor problem  
> tiny object

让用户判断哪些搭配自然。

这训练的是词汇空间里的**边界**。

一个成熟词汇不是一个孤立节点，而应该逐渐形成：

\[
\text{subtle}
\leftrightarrow
\{\text{slight, nuanced, delicate, implicit...}\}
\]

这样的语义网络。

---

5. **自由运用（Generative Use）**

当词已经比较熟以后，不能一直填空。

系统应该开始要求用户自己创造语言。

例如：

> 用 `subtle` 写一句和大学生活有关的话。

用户：

> There is a subtle difference between the two professors' teaching styles.

AI 判断：

- 词义是否正确；
- 语法是否正确；
- 搭配是否自然；
- 是否真的体现了 subtle 的核心语义。

然后可能反馈：

> 用法自然。这里的 subtle 很合适，因为你表达的是“存在区别，但不明显”。

更进一步，AI 可以给用户一个情景：

> 你的朋友换了发型，但变化很小。用 `subtle` 描述。

这比“造句”更好，因为它规定了**communicative intent**。

用户不是为了完成题目而造句，而是为了表达一个东西主动调用这个词。

---

6. **迁移测试（Transfer）**

这是我认为你的产品非常值得做、而且 AI 特别适合的一种模式。

一个词连续答对几次以后，不要继续考类似题。

直接改变环境。

例如 `subtle` 之前一直出现在日常语境，现在突然：

> The experiment revealed a subtle deviation from the theoretical prediction.

或者：

> The author makes a subtle distinction between freedom and autonomy.

或者听力式：

> AI 读一句话，让用户判断含义。

甚至不明确告诉用户：

> “现在正在复习 subtle。”

让它自然混在其他内容里。

如果用户仍然能够调用：

> subtle

那么这才提供了很强的 mastery evidence。

这里本质上是在检查：

\[
\text{knowledge learned in context A}
\rightarrow
\text{usable in context B}
\]

这就是你之前担心的“记住语料而没记住词”的真正解决办法。

---

但还有一个非常重要的设计：**不要让这六种模式变成六个菜单按钮。**

我不建议软件首页出现：

> 单词选择题  
> 填空  
> 造句  
> 近义词辨析  
> 阅读理解  
> ……

那还是传统软件的思维。

用户最好只做一件事：

> **开始学习。**

系统内部根据当前 learner model 自动决定下一次交互。

比如某个词：

> `subtle`

内部状态可能是：

> 初始理解：0.85  
> 语境理解：0.78  
> 主动提取：0.31  
> 辨析：0.42  
> 跨语境稳定性：未知

那么下一次根本没有理由继续给他做“subtle 英文选中文”。

系统应该自动选择：

> **production**

给：

> The difference exists, but it's difficult to notice.  
> Rewrite it using one adjective you've learned.

如果用户成功：

> production ↑

然后过几天换一个完全不同的环境测试。

所以，从用户视角：

> 学习 → 回答 → 反馈 → 下一题

非常简单。

从系统视角，却是：

> 选择要测的能力  
> → 选择目标词义  
> → 选择交互模式  
> → 生成合适语境  
> → 收集行为证据  
> → 更新 learner model  
> → 决定下一次训练

复杂度应该藏在 AI 后面，而不是暴露给用户。

还有一个我认为很值得加入的交互：**“不确定”按钮。**

传统软件通常只有：

> 正确 / 错误

但记忆其实不是二元状态。

用户看到：

> The change was very ______.

可能想：

> “我知道是 s 开头，好像是 subtle，但不确定拼写。”

这和“完全不会”是完全不同的记忆状态。

因此可以允许：

> 会  
> 不确定  
> 不会

甚至更自然地直接让 AI 从行为判断：

- 秒答正确 → 强证据
- 犹豫 15 秒正确 → 弱一些
- 看提示后正确 → 更弱
- 写出 `slight` → 概念部分正确但目标词提取失败
- 完全不会 → retrieval failure

于是**用户怎么答**本身就成为学习数据。

我最终会把整个交互系统压缩成三个用户真正能感知到的动作：

> **理解东西 → 想起东西 → 使用东西**

而内部则对应：

**Encounter → Comprehension → Recall → Discrimination → Use → Transfer**

这已经足够组成完整学习循环。

其中还有一个关键问题会直接影响整个软件的体验：**答错以后怎么办？**

因为“显示正确答案然后下一题”其实非常低效。完全不会、差一点想起来、混淆了近义词、拼写错误、理解错词义，这五种错误应该触发完全不同的下一步交互。

我认为下一步最值得把这个 **“错误 → AI 如何诊断 → 下一步该做什么”** 的反馈机制设计出来。它很可能是这个产品真正区别于 Anki、百词斩这一类软件的核心。