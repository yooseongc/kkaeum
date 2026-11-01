package com.kkaeum.alarm

import kotlin.random.Random

/** Register a generator here to reuse an existing touch interaction without changing alarm delivery. */
internal object PuzzleGenerators {
    class Question(val level: Int, val choice: String, val random: Random) {
        val shapes = listOf("●", "▲", "■", "◆", "★", "＋")
        var labels = emptyList<String>()
        var solution = emptyList<Int>()
        var board = emptyList<Int>()
        var columns = 3
        var prompt = ""
    }
    private val generators: Map<String, Question.() -> Unit> = mapOf(
        "odd" to {
            val count = 3 + level * 3
            val pair = shapes.shuffled(random).take(2); val target = random.nextInt(count)
            labels = List(count) { if (it == target) pair[1] else pair[0] }; solution = listOf(target)
            prompt = "다른 모양을 누르세요"
        },
        "order" to {
            val count = 3 + level * 2
            labels = (1..count).shuffled(random).map { it.toString() }
            solution = (1..count).map { labels.indexOf(it.toString()) }; prompt = "1부터 차례로 누르세요"
        },
        "memory" to {
            labels = List(9) { "" }; solution = (0..8).shuffled(random).take(level + 1)
            prompt = "켜진 칸을 기억하세요"
        },
        "pairs" to {
            val count = level + 2
            labels = (shapes.take(count) + shapes.take(count)).shuffled(random)
            solution = emptyList(); prompt = "같은 그림 두 장을 찾으세요"
        },
        "sequence" to {
            columns = 2; labels = listOf("1", "2", "3", "4")
            solution = List(level + 2) { random.nextInt(4) }; prompt = "켜지는 순서를 기억하세요"
        },
        "connect" to {
            columns = 2
            val pairs = listOf("비" to "우산", "밤" to "달", "겨울" to "눈사람", "바다" to "물고기", "생일" to "케이크")
                .shuffled(random).take(level + 2)
            val right = pairs.map { it.second }.shuffled(random)
            labels = pairs.indices.flatMap { listOf(pairs[it].first, right[it]) }
            solution = pairs.indices.map { right.indexOf(pairs[it].second) * 2 + 1 }
            prompt = "왼쪽과 어울리는 오른쪽 항목을 누르세요"
        },
        "slide" to {
            board = (1..8).toList() + 0
            var previous = -1
            repeat(3 + level * 3) {
                val blank = board.indexOf(0)
                val next = board.indices.filter { TouchMission.adjacent(it, blank) && it != previous }.random(random)
                board = board.toMutableList().apply { this[blank] = this[next]; this[next] = 0 }
                previous = blank
            }
            if (board == (1..8).toList() + 0) {
                board = board.toMutableList().apply { this[8] = this[7]; this[7] = 0 }
            }
            labels = List(9) { "" }; solution = emptyList(); prompt = "빈칸 옆 조각을 눌러 그림을 맞추세요"
        },
        "choice" to {
            val variant = if (choice == "mixed") listOf("pattern", "count", "direction", "category").random(random) else choice
            var correct: String
            when (variant) {
                "pattern" -> {
                    val s = shapes.shuffled(random).take(3)
                    if (level == 1) { prompt = "${s[0]}  ${s[1]}  ${s[0]}  ${s[1]}  ?"; correct = s[0] }
                    else if (level == 2) { prompt = "${s[0]}  ${s[0]}  ${s[1]}  ${s[0]}  ${s[0]}  ?"; correct = s[1] }
                    else { prompt = "${s[0]}  ${s[1]}  ${s[2]}  ${s[0]}  ${s[1]}  ?"; correct = s[2] }
                    labels = s.shuffled(random)
                }
                "count" -> {
                    val amount = random.nextInt(2, 4 + level)
                    prompt = "별은 몇 개일까요?\n" + (List(amount) { "★" } + List(level + 1) { "●" }).shuffled(random).chunked(4).joinToString("\n") { it.joinToString("  ") }
                    correct = amount.toString(); labels = (amount - 1..amount + 2).map { it.toString() }.shuffled(random)
                }
                "direction" -> {
                    val directions = listOf("위" to "↑", "아래" to "↓", "왼쪽" to "←", "오른쪽" to "→")
                    val target = directions.random(random); prompt = "${target.first}를 향한 화살표는?"
                    correct = target.second; labels = directions.map { it.second }.shuffled(random)
                }
                else -> {
                    val items = listOf("동물이 아닌 것은?" to listOf("고양이", "강아지", "토끼", "의자"),
                        "탈것이 아닌 것은?" to listOf("버스", "기차", "자전거", "사과"),
                        "과일이 아닌 것은?" to listOf("사과", "포도", "바나나", "연필")).random(random)
                    prompt = items.first; correct = items.second.last(); labels = items.second.shuffled(random)
                }
            }
            columns = if (labels.size == 3) 3 else 2; solution = listOf(labels.indexOf(correct))
        }
    )
    fun create(type: String, level: Int, choice: String, seed: Long): Question =
        Question(level, choice, Random(seed)).apply { generators.getValue(type).invoke(this) }
}
