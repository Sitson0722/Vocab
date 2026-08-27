ME: 我想开发一个ai驱动的背单词软件。我们现在不讨论具体实现，只讨论原理和第一性原理分析。
我目前的想法：
1，利用遗忘曲线复习
2，利用ai生成语料
3，双维学习：一是理解，二是运用
4，语料可以是短语
5，复习多次使用过的语料，要用新生成的代替，放置记住语料忘了单词。
这些只是抽象的ideas，让我们讨论一下，具体一下，完善一下

可以。先把“背单词软件”这个概念拆掉，从第一性原理看，它真正要解决的不是“让用户记住单词”，而是：

> 让一个词在用户真正需要理解或表达的时候，能够被正确、快速地调用出来，并且长期保持这种能力。

这一定义会直接改变软件设计。因为“我见过这个词”“我知道中文意思”“我能在句子里理解”“我自己说话时能想起来”其实是四种完全不同的状态。

你的 5 个 idea 已经指向了几个关键方向，但我会把它们重新组织成一个更完整的理论框架。

一、最基本的学习对象不应该是“单词”，而应该是“词义 + 使用模式”

例如：

> charge

如果软件把它当成一个单词：

> charge = 收费 / 指控 / 冲锋 / 电荷……

学习对象已经混乱了。

真正应该学习的是类似：

> charge₁：收取费用  
> charge someone $10  
> charge for something

以及：

> charge₂：指控  
> charge someone with murder

再比如物理中的：

> electric charge

所以系统内部更合理的最小单位是：

**lexical sense（词义） + usage pattern（使用模式）**

而不是字符串 `"charge"`。

这也解释了你为什么提到“语料可以是短语”。我会再往前推进一步：

> 短语、搭配和句型不是单词的附属信息，而是单词知识本身的一部分。

比如一个人知道：

> depend = 依赖

却说：

> depend something

那么他其实并没有真正掌握 depend。

因此，一个学习项目可以长这样：

> depend on sth  
> be heavily dependent on sth  
> It depends on whether...

而不是：

> depend —— 依靠

这可能是整个软件最重要的底层原则之一。

---

二、你说的“双维学习：理解 + 运用”非常重要，但可以再精确一点

可以把一个词的掌握状态想象成至少两个核心变量：

\[
C = comprehension
\]

\[
P = production
\]

即：

**理解能力 C：** 看到这个词时能不能理解。

**产出能力 P：** 想表达一个意思时能不能主动想到并正确使用这个词。

通常：

\[
C > P
\]

也就是说，“看得懂”永远比“自己会用”容易。

传统背单词软件最大的问题之一，就是大量训练 C，然后把它误认为 mastery。

例如：

> abandon  
> A. 放弃  
> B. 建立  
> C. 讨论  
> D. 购买

即使用户连续答对十次，也只能说明：

> recognition 很强。

却不能推出：

> production 很强。

所以同一个词应该经历不同方向的 retrieval：

英语 → 意义：

> He abandoned the plan after several failed attempts.

问：

> abandoned 在这里是什么意思？

训练理解。

意义 → 英语：

> “在几次失败后，他___了这个计划。”

训练主动提取。

开放表达：

> 用 abandon 表达“政府最终放弃了这个项目”。

进一步训练运用。

甚至：

> Describe a situation in which someone might abandon a plan.

这是更真实的 production。

这就形成了一个非常重要的原则：

> 学习不是“反复看到”，而是反复进行 retrieval。

---

三、“遗忘曲线”应该升级成“动态记忆模型”

你第一条说：

> 利用遗忘曲线复习

方向对，但“遗忘曲线”容易让产品设计走向一个误区：

> 一个词学完 → 1 天 → 3 天 → 7 天 → 14 天复习。

实际上每个词的记忆强度不同，每个人不同，甚至同一个人对同一个词的不同能力也不同。

比如：

> ubiquitous

某个用户可能：

看到 → 秒懂。

但问：

> “无处不在的”英文是什么？

完全想不起来。

那么这个词的状态不是一个数字：

> mastery = 70%

而应该至少有：

> comprehension strength = 高  
> production strength = 低

甚至进一步：

> meaning recognition  
> form recall  
> collocation  
> grammar  
> contextual discrimination

都是不同状态。

因此真正值得做的不是：

> 根据“第几次复习”安排下一次。

而是：

> 根据系统估计的“下一次提取失败概率”安排复习。

可以抽象成：

\[
P(\text{successful recall at time }t)
\]

软件不断估计这个概率。

如果现在：

\[
P=0.97
\]

再考一次意义不大。

如果：

\[
P=0.75
\]

可能正是一个很好的 retrieval 时机。

如果：

\[
P=0.15
\]

可能已经忘得太严重，需要重新学习而不是单纯测试。

所以你的核心系统其实不是“遗忘曲线”，而是一个：

> learner state estimation system

AI 每次和用户互动，都在更新对用户词汇知识状态的估计。

---

四、AI 最有价值的地方并不是“生成例句”

这是我认为你的 idea 里可以继续挖得最深的地方。

“AI 生成语料”只是表面功能。

真正有价值的是：

> AI 可以生成恰好适合当前学习状态的测试环境。

例如用户正在学习：

> subtle

第一次：

> There is a subtle difference between the two theories.

简单明确。

第二次：

> She gave him a subtle hint that it was time to leave.

换语义环境。

第三次：

> The effect is subtle but statistically significant.

再换环境。

第四次，不直接出现 subtle：

> The difference exists, but it is small and difficult to notice.  
> What adjective could replace this description?

用户需要主动提取：

> subtle

这时 AI 的作用已经不是“内容生成器”，而是：

> 自适应训练环境生成器。

这是一个非常大的区别。

---

五、你的第 5 条非常关键：必须防止“记住题目，而不是学会单词”

你说：

> 复习多次使用过的语料，要用新生成的代替，防止记住语料忘了单词。

完全正确。

这背后其实是一个普遍问题：

> stimulus memorization ≠ concept learning

比如 Anki 卡片：

> The scientist ___ the hypothesis.

用户看到 “scientist + hypothesis” 就条件反射：

> rejected

甚至没有真正调用 rejected 的词义。

所以训练一个词应该追求：

> contextual invariance

也就是：

无论上下文怎么变，用户仍然能够识别或调用这个词。

可以想象一个词经历：

> context A → context B → context C → context D

如果四个环境完全不同，用户都能正确理解：

这才是更强的证据：

> 用户掌握的是词，而不是句子。

不过这里有一个需要修正的地方：

**不能每次都完全随机换句子。**

因为如果：

单词本身难度 = 5

同时：

句子结构难度 = 8  
背景知识难度 = 7  
其他生词数量 = 5

用户答错时系统就不知道：

> 他是不认识目标词，还是没读懂这个句子？

所以 AI 生成语料时应该控制变量。

理想情况是：

> 目标词是主要未知变量，其余上下文尽量处于用户舒适区。

这实际上引出了一个非常重要的 AI 能力：

> controlled generation

而不只是 generation。

---

六、我认为还应该加入第三个维度：区分能力

理解和运用之外，还有一个经常被忽略的问题：

> 我知道这个词是什么意思，但我不知道什么时候该用它。

例如：

> big  
> large  
> huge  
> enormous  
> massive

如果题目问：

> massive = 巨大的

用户可能会。

但：

> massive problem  
> massive building  
> massive amount of data  
> massive mistake

哪些自然？

以及：

> big rain

为什么不自然？

真正的词汇能力很大一部分来自：

> lexical discrimination

也就是：

**这个词和附近的词有什么边界？**

再比如：

> economic  
> economical

或者：

> historic  
> historical

或者：

> assume  
> presume

所以我甚至会把最核心模型改成三个轴：

\[
\text{Vocabulary mastery}
=
(C,\ P,\ D)
\]

其中：

C = comprehension，理解  
P = production，产出  
D = discrimination，辨析

这是比单纯“认识/不认识”强很多的模型。

---

七、这样一来，整个学习过程其实是一个闭环

可以把软件抽象成：

> 观察 → 推断 → 出题 → 提取 → 更新

系统首先有一个内部模型：

> 用户目前对 subtle 的理解不错，但主动使用很弱。

然后选择训练目标：

> production

AI 生成：

> “The difference is real, but very difficult to notice.”  
> 用一个你学过的形容词改写。

用户回答：

> slight

这个答案其实并不一定错。

于是系统进一步发现：

> 用户知道 slight，但还没有建立 subtle 和这个语义空间之间的区别。

下一题可能变成：

> slight / subtle  
> 哪一个更强调“难以察觉”？

于是一次普通的“背单词”变成了：

> diagnosis → targeted intervention

这才是 AI 真正能超越 Anki 的地方。

---

八、最终目标不应该是“今天背 50 个词”

传统软件的单位是：

> 今日学习：50 words

但真正合理的目标应该类似：

> 今天进行了 37 次有效 retrieval  
> 加强了 12 个薄弱 lexical senses  
> 4 个词从 recognition 转入 production  
> 发现 3 组容易混淆词

也就是说：

> word count 是 input metric。

而真正应该优化的是：

> capability change。

甚至可以定义某个词的 mastery：

\[
M(w)=f(C,P,D,R)
\]

其中 R 可以表示：

> robustness across contexts

即在不同语境下是否稳定。

那么：

> “mastered”

就不再意味着“答对三次”，而意味着：

> 我能理解它。  
> 我能主动想起它。  
> 我知道怎么搭配。  
> 我能把它和近义词区分开。  
> 换一个陌生语境我仍然会。

这就非常接近真正的词汇能力了。

---

所以如果把你目前的 5 个 idea 整合一下，我认为这个产品的核心思想可以变成一句话：

> **不是用 AI 帮用户反复记忆单词，而是用 AI 持续估计用户的词汇能力，并生成恰好能暴露和修复当前薄弱点的新语境。**

这两种产品看起来可能非常像，但底层完全不同。

传统模型是：

> 单词 → 卡片 → 复习 → 记住

你的模型更有潜力变成：

> lexical item → learner model → adaptive retrieval → evidence → model update → adaptive retrieval

而且这里还有一个很有意思的进一步问题尚未解决：

**什么时候应该“教”，什么时候应该“考”？**

例如一个完全陌生的词，直接让用户猜没有意义；但如果一直展示解释，又没有 retrieval，就很难形成长期记忆。

因此下一层最值得讨论的其实是：**一个词从“第一次遇见”到“真正掌握”，到底应该经历哪些状态和转移。**

如果这个状态机先设计清楚，后面的 AI、遗忘算法、题型、语料生成其实都会自然很多。

ME: 你写的很好，尤其是四和六。一词多义也想办法确保我不重不漏掌握每个义项。
现在详细的总结一下你的这部分开发思路，不要漏，但是不需要讲如何实现，只要确保功能和逻辑不缺失和断层就行。
后期我要用agent开发，确保这份总结（不是提示词）能让这些点子得到充分实现（不用讲怎么编程）。

可以把整个产品的完整工作流理解成一条持续运行的学习管线。它不是“选单词 → 背 → 复习”，而是：

> 词汇进入系统 → 拆成义项与用法 → 判断优先级 → 建立初始知识 → 多维训练 → 诊断薄弱点 → 自适应生成新语境 → 间隔复习 → 验证跨语境稳定性 → 补齐其他义项 → 长期维护。

下面给出完整闭环。

# 一、词汇进入系统

一个单词第一次进入系统时，系统不能立刻开始出题。

首先要把这个 word 建立成完整的“词汇对象”。

例如：

> charge

系统需要认识它有哪些目标义项，例如：

- 收费
- 指控
- 电荷
- 冲锋
- 掌管、负责相关表达

然后对每个义项进一步整理：

- 核心含义
- 常见搭配
- 常见语法结构
- 典型短语
- 常见语境
- 近义词
- 易混词
- 使用频率
- 语域
- 用户专业相关性

形成类似：

```text
charge

Sense 1：收费
  核心含义
  charge someone $10
  charge for something
  charge a fee
  邻近词：cost / fee / bill

Sense 2：指控
  charge someone with...
  邻近词：accuse

Sense 3：电荷
  electric charge
  positive charge
  negative charge

...
```

这一阶段解决的是：

> “这个词到底包含什么需要学习的知识？”

---

# 二、确定“目标义项范围”

不应该把词典里所有极罕见义项都塞给用户。

系统需要确定：

> 哪些义项属于当前学习目标。

判断依据包括：

- 实际语言中的频率
- 用户当前英语水平
- 用户领域需求
- 用户已有知识
- 未来出现概率
- 学习价值

因此可能得到：

```text
charge

必须掌握：
1. 收费
2. 指控
3. 电荷

暂缓：
4. 冲锋

当前忽略：
5. 极罕见历史用法
```

这里很重要的一条原则是：

> 暂缓 ≠ 已掌握。

系统必须记住哪些义项尚未学习。

这样才能真正做到“一词多义不重不漏”。

---

# 三、评估用户已有知识

一个词进入系统后，不一定需要从零开始。

用户可能已经：

- 完全不认识
- 见过
- 知道一个义项
- 知道多个义项
- 看得懂但不会用
- 会用但搭配有问题
- 经常和另一个词混淆

所以系统应该先进行最低成本的诊断。

例如：

看到：

> charge

用户可能立即知道：

> 电荷

但不知道：

> charge someone with murder

那么系统应该建立：

```text
charge

收费：
C：未知
P：未知
D：未知

指控：
C：未知
P：未知

电荷：
C：较强
P：中等
```

这样后面不会浪费大量时间重复教学。

---

# 四、决定当前应该学习哪个义项

系统不能同时展开所有义项。

应该按优先级逐渐开放。

通常流程可以是：

> 高频核心义项 → 高频第二义项 → 用户领域相关义项 → 低频义项

例如 charge：

第一阶段可能先学习：

> 收费

之后再引入：

> 指控

再之后：

> 电荷

如果用户是物理专业，电荷义项则可能提前。

因此系统始终有一个：

> 当前 active sense。

---

# 五、第一次教学：建立基本心理表征

如果用户完全不认识某个义项，就不能直接不断考试。

此时进入 Teaching 阶段。

目标不是让用户一次性掌握全部知识，而只是建立最核心表征。

例如：

> subtle

第一次可以建立：

> subtle：不明显的、细微而难以察觉的

配一个非常清楚的例子：

> There is a subtle difference between the two theories.

然后让用户理解：

> subtle 的核心不是单纯“小”，而是“细微、不容易察觉”。

同时可以引入一个最典型搭配：

> subtle difference

但不应该第一次就塞入十个搭配、五个近义词和三个义项。

原则是：

> progressive enrichment。

---

# 六、立即进行轻度提取

教学完成后不能直接结束。

需要马上让用户进行一次比较容易的 retrieval。

例如：

> The difference was very small and difficult to notice.

选择：

> obvious / subtle / enormous

这一步主要检查：

> 初始概念是否建立。

如果用户连这个都失败，就说明 Teaching 没有成功。

如果成功，则进入下一阶段。

---

# 七、同义项的理解训练

接下来训练：

> Comprehension。

系统不断改变语境，但保持同一义项。

例如 subtle：

> a subtle change

> a subtle hint

> a subtle difference

> a subtle shift in tone

目标是确认：

> 用户能否在陌生语境里正确识别同一个 sense。

这一步尤其需要避免重复原句。

因为系统真正要测试的是：

> sense recognition

而不是：

> example memorization。

---

# 八、从 Recognition 转向 Recall

当用户理解开始稳定后，任务应该逐渐减少提示。

例如从：

> 选择 subtle

变成：

> 填空

再变成：

> 根据意思主动回忆。

例如：

> “一个存在但很难察觉的差异”

要求用户想到：

> subtle difference

这时开始训练：

> Production。

---

# 九、从词形回忆转向真实表达

Production 不能永远停留在填空。

之后应该逐渐进入：

> constrained production

例如：

> 用 subtle 改写：

> The difference is difficult to notice.

然后：

> semi-open production

例如：

> 用 subtle 描述实验结果中的一个很小但真实的效应。

最后可以进入：

> free production

例如：

> Describe a subtle change you have noticed in someone's behavior.

这里检验的已经不只是：

> 能不能想起单词。

而是：

> 能不能自然使用。

---

# 十、学习核心搭配与使用模式

当基本词义建立以后，系统逐步加入：

- 搭配
- 介词
- 句法
- 固定结构

例如：

> depend

不能只训练：

> depend = 依赖。

需要继续确认：

> depend on something

> depend heavily on

> It depends on whether...

这一部分应该逐渐融入 Production 和 Comprehension，而不是单独背“搭配表”。

---

# 十一、引入近义词和易混词

当一个词的基本意义稳定之后，开始训练：

> Discrimination。

例如：

> subtle vs slight

先明确区别。

然后进入对比：

> a slight increase

> a subtle difference

再通过新语境训练：

> 哪个更自然？

之后进一步：

> 为什么另一个不合适？

最终要求用户自己使用。

这一阶段解决的是：

> “我知道这个词，但什么时候该用它？”

---

# 十二、发现错误时进入诊断分支

这里是整个系统最重要的工作流之一。

用户答错后不能统一处理。

例如系统要求 subtle，用户回答 slight。

系统首先要判断可能原因。

可能是：

### A. 完全忘记 subtle

则进入：

> retrieval reinforcement。

### B. 知道 subtle，但没有主动想到

则加强：

> production。

### C. subtle / slight 混淆

则进入：

> discrimination。

### D. 词义知道，但搭配错误

则训练：

> usage pattern。

### E. 句子本身没看懂

则：

> 不应错误地下调目标词 mastery。

### F. 偶然拼写错误

则：

> 不应等同于词义遗忘。

所以错误触发的是：

```text
错误
↓
诊断
↓
确定薄弱点
↓
针对性修复
↓
新语境重新测试
```

而不是：

```text
错误
↓
显示答案
↓
下一题
```

---

# 十三、错误修复必须经过“再次验证”

解释完 subtle 和 slight 的区别以后不能认为问题解决了。

系统应该稍后换一个新语境再次测试。

例如：

> The change was so ___ that almost nobody noticed it.

再次验证。

如果用户成功：

> discrimination evidence 增强。

如果仍然失败：

> 说明之前的解释没有真正转化成能力。

所以：

> correction 不等于 learning。

只有：

> correction → new retrieval success

才算真正修复。

---

# 十四、建立跨语境鲁棒性

当一个义项开始比较熟练后，系统需要刻意增加语境变化。

例如：

> subtle difference

之后：

> subtle humor

> subtle change in behavior

> subtle effect in an experiment

同时变化：

- 主题
- 人物
- 句型
- 文体
- 专业环境
- 语气

目标是：

> 检查用户是否形成 context-independent knowledge。

如果用户只在：

> scientific context

认识 subtle，

却在：

> social interaction

完全认不出来，

说明 robustness 仍然不足。

---

# 十五、逐渐减少中文中介

早期可以：

> subtle → 细微、不明显

但随着熟练度提高，训练应逐渐转向：

> English → concept

以及：

> concept → English

例如：

> difficult to notice, though real

→ subtle

而不是始终：

> 细微的 → subtle。

这样避免用户形成：

> English → Chinese → meaning

的强制翻译链条。

---

# 十六、暂时停止训练，进入间隔阶段

当当前能力足够稳定后：

> 不应该继续连续刷。

此时系统需要停止训练，让遗忘自然发生。

进入：

> delayed retrieval。

例如过一段时间以后重新测试。

重点不是简单根据：

> 第几次复习。

而是：

> 这个 lexical sense 当前大概处于什么记忆状态。

---

# 十七、间隔复习时生成新材料

再次出现时原则上：

> 不直接复用旧题。

例如第一次：

> a subtle difference between two theories

下次：

> a subtle shift in public opinion

再下次：

> subtle signs of fatigue

这样测试：

> 用户还能否从新的 stimulus 中调用相同知识。

旧语料偶尔可以再次出现，但不能成为 mastery 的主要证据。

---

# 十八、复习结果更新各维度，而不是单一熟练度

每次复习之后分别更新：

```text
Sense X

Comprehension
Production
Discrimination
Context robustness
Retention
```

例如：

> 看到 subtle 秒懂

只增强：

> comprehension + retention。

如果要求：

> “难以察觉的差异”英文是什么？

用户主动回答：

> subtle difference

则主要增强：

> production + retention。

如果 subtle/slight 对比正确：

> discrimination 增强。

因此不同题型提供的是不同 evidence。

---

# 十九、发现“假熟练”

系统要主动寻找下面这种情况：

> 用户一直答对，但证据来源太单一。

例如：

```text
subtle

10 次理解题正确
0 次主动产出
0 次辨析
2 个高度相似语境
```

这时不能认为：

> mastered。

而应该判断：

> evidence imbalance。

随后主动补：

> Production / Discrimination / Context variation。

这一步非常重要。

---

# 二十、一个义项达到阶段性掌握

当一个 sense 拥有足够多独立证据，例如：

- 多个陌生语境能理解
- 可以主动提取
- 可以正确使用核心结构
- 能与主要近义词区分
- 跨主题稳定
- 时间间隔后仍然成功

则进入：

> maintained state

或者：

> currently mastered。

注意不是永久完成。

只表示：

> 当前掌握证据充分。

---

# 二十一、切换到同一个词的下一个义项

这时系统回到：

> Sense selection。

例如 charge：

已经掌握：

> charge = 收费。

现在开始：

> charge = 指控。

此时需要明确告诉用户：

> 同一个词在这里是另一个意思。

然后重新经历：

```text
Teaching
↓
Comprehension
↓
Production
↓
Usage
↓
Discrimination
↓
Context robustness
↓
Delayed retrieval
```

但旧义项不会停止维护。

---

# 二十二、建立义项之间的区分

当一个多义词已经学过多个 sense 后，需要增加特殊训练：

> sense discrimination。

例如：

> charge

在不同句子里判断：

```text
The hotel charged us $20.
→ 收费

He was charged with theft.
→ 指控

The particle has a negative charge.
→ 电荷
```

之后进一步不再直接问：

> “是哪一个意思？”

而是通过理解或生成任务自然检验。

目标是：

> 同一个词形出现时，用户能根据语境快速选择正确 sense。

---

# 二十三、多义词完成度检查

系统定期检查：

```text
charge

Sense A：掌握
Sense B：掌握
Sense C：未学
Sense D：部分掌握
```

因此绝不能显示：

> charge 已完成

除非目标范围中的各重要义项都得到处理。

这就是解决：

> 不漏。

同时系统应该合并重复或高度重叠的训练目标，避免同一个 sense 被伪装成多个“新义项”。

这就是解决：

> 不重。

---

# 二十四、进入综合语言环境

当多个词都达到一定熟练度后，不再永远单词级孤立训练。

系统可以生成：

- 一段短文
- 对话
- 小故事
- 学术段落
- 真实场景

自然包含多个已学词。

例如一段物理实验文本可能同时包含：

> subtle  
> significant  
> derive  
> approximation  
> negligible

此时产生：

> integrated retrieval。

目标是检查：

> 用户在真实语言环境里还能不能调用这些词。

---

# 二十五、在综合任务中发现新的弱点

用户可能在单词卡式训练中表现很好，但在真实文本中失败。

例如：

> subtle 单独测试一直正确，

但放进长段落：

> 用户忽略了 subtle 对句意的影响。

那么系统得到新的 evidence：

> isolated comprehension strong  
> integrated comprehension weaker

随后重新进入：

> targeted training。

因此整个系统没有真正的“毕业”。

而是不断：

> 使用真实表现重新修正 learner model。

---

# 二十六、系统还需要主动发现未知缺口

不仅用户输入的单词才能学习。

系统应该通过学习过程中出现的行为发现：

> 用户可能还缺少哪些词汇知识。

例如：

用户反复用：

> very big

表达各种含义。

系统可能发现：

> massive / enormous / substantial

存在词汇空缺。

又例如：

用户已经知道 issue = 问题，

但在：

> The government issued a statement.

中失败。

那么发现：

> issue 的一个重要义项缺失。

这种情况进入：

```text
Gap discovery
↓
判断是否值得学习
↓
加入 lexical model
↓
进入正常学习流程
```

这就是系统解决：

> “我不知道自己不知道什么。”

---

# 二十七、每天开始学习时，系统不是简单打开“今日单词”

每天的 session 应该从：

> 选择当前最有价值的学习动作

开始。

候选任务可能同时包括：

```text
A. 即将遗忘的旧义项
B. Production 明显落后的词
C. 最近暴露出的混淆词
D. 尚未学习的重要新义项
E. 需要跨语境验证的词
F. 长期未测试的已掌握词
G. 新词
```

然后根据当前 learner state 选择最值得训练的内容。

因此用户看到的是：

> 一条流畅的学习流。

背后实际上不断切换不同目的。

---

# 二十八、单次学习 Session 的内部循环

一次 session 可以抽象成：

```text
1. 读取当前 learner state

2. 找出最值得处理的问题

3. 选择：
   - 新词
   - 旧词
   - 某义项
   - 某能力维度

4. 判断：
   Teaching 还是 Retrieval？

5. 如果 Retrieval：
   选择训练目的
   - comprehension
   - production
   - discrimination
   - robustness
   - retention

6. 生成合适语境

7. 用户作答

8. 分析表现

9. 判断：
   - 正确
   - 部分正确
   - 错误类型
   - 提示依赖
   - 反应难度

10. 更新 learner model

11. 如果发现新问题：
    转入修复分支

12. 如果当前问题解决：
    选择下一个最高价值任务

13. 当学习收益开始下降：
    结束 Session
```

这就是最核心的运行循环。

---

# 二十九、单个 lexical sense 的完整生命周期

如果压缩成一条状态链，可以写成：

```text
未发现
↓
已发现但未学习
↓
首次教学
↓
初步理解
↓
可识别
↓
可主动回忆
↓
可正确使用
↓
掌握核心搭配
↓
能够与相近词辨析
↓
跨语境稳定
↓
短期间隔保持
↓
长期间隔保持
↓
当前掌握
↓
维护
```

任何阶段都可能发生：

```text
遗忘
混淆
语境依赖
生产退化
```

于是状态回退，再训练。

---

# 三十、一个 Word 的完整生命周期

一个 word 由多个 sense 构成。

所以：

```text
Word discovered
↓
建立义项地图
↓
确定目标义项
↓
Sense 1 学习
↓
Sense 1 维护
↓
Sense 2 学习
↓
Sense 1 + Sense 2 区分
↓
Sense 3 学习
↓
多义项综合识别
↓
全部目标义项进入维护
↓
Word-level mastery
```

以后即使 word-level mastery 已经建立：

> 某个 sense 仍可能单独退化。

---

# 三十一、整个系统的长期大循环

最终完整产品实际上运行三个相互嵌套的循环。

最内层：

```text
一道练习
↓
回答
↓
诊断
↓
更新
```

中层：

```text
一个 lexical sense
↓
Teaching
↓
Retrieval
↓
Generalization
↓
Retention
↓
Maintenance
```

最外层：

```text
整个用户词汇网络
↓
发现缺口
↓
确定优先级
↓
学习
↓
维护
↓
发现新的薄弱点
↓
继续扩展
```

所以整个产品没有真正意义上的：

> “背完”。

它更像一个持续维护的：

> personal lexical model。

---

# 三十二、最终工作流总图

可以把所有逻辑压缩成这一条主线：

```text
词汇来源
↓
建立 Word
↓
拆分目标 Senses
↓
建立 Usage / Collocation / Semantic Neighbors
↓
检查用户已有知识
↓
选择当前最重要 Sense
↓
判断是否需要 Teaching
↓
建立初始意义
↓
Guided Retrieval
↓
Comprehension Training
↓
Production Training
↓
Usage Training
↓
Discrimination Training
↓
Context Variation
↓
Delayed Retrieval
↓
更新 C / P / D / R / Retention
↓
诊断薄弱点
↓
针对性修复
↓
新语境验证
↓
阶段性 Mastery
↓
切换下一个 Sense
↓
多义项区分
↓
Word-level completeness check
↓
综合文本 / 对话 / 表达训练
↓
长期维护
↓
发现遗忘或新缺口
↓
重新进入对应学习环节
```

最关键的是，这不是固定线性流程。

它真正应该运行成：

> **状态驱动的动态网络。**

例如用户不需要每次都经过完整的：

> 理解 → 产出 → 辨析。

如果系统已经知道：

> comprehension 极强，production 很弱，

下一次可以直接进入：

> production。

如果：

> production 很好，但 subtle/slight 总混淆，

就直接进入：

> discrimination。

如果所有能力都很好，但三个月没见：

> 直接进行 delayed retrieval。

因此“完整工作流”规定的是：

> 所有必要环节必须存在，而且之间不能断层；

但具体某个词走哪条路径，应由 learner state 决定。

整个产品最终可以归结成一个核心原则：

> **每次学习都先判断“当前最缺什么”，然后只训练那个缺口；每次训练结果再反过来修改判断。**

这样前面讨论的多义词、理解/产出/辨析、AI 新语境、遗忘、错误诊断、语境泛化、长期维护，就全部被串成一个完整系统了。