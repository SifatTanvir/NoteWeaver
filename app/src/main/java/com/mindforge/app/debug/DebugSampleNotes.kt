package com.mindforge.app.debug

/**
 * Synthetic notes long enough for merge / similarity heuristics (min length/word counts in [NoteRepository]).
 */
object DebugSampleNotes {
    /**
     * Pairs of (title, content). Content is substantive so BERT grouping and merge suggestions activate.
     */
    val presets: List<Pair<String, String>> =
        listOf(
            Pair(
                "Team standup — Oct 14",
                """
                Discussed blocker on API latency spikes during peak hours.
                Backend will add caching on popular endpoints within this sprint deadline.
                We agreed to rerun load tests tomorrow morning before the client demo on Friday afternoon.
                """.trimIndent()
            ),
            Pair(
                "Standup recap (Oct 14)",
                """
                Today we touched on slowdowns hitting our API whenever traffic peaks.
                The plan is to ship caching layers for hot paths before sprint end.
                QA should rerun load profiling Wednesday ahead of Friday client-facing walkthrough session.
                """.trimIndent()
            ),
            Pair(
                    "BERT notes — research log",
                    """
                    Paraphrase models score sentence pairs by encoding context windows from title and opening paragraphs.
                    For near-duplicate UX we rely on quantized ONNX runtime on device with cosine-style similarity probes.
                    This paragraph exists so the note clears minimum character counts for classifier stages downstream.
                    """.trimIndent()
            ),
            Pair(
                    "Paraphrase detection draft",
                    """
                    We compare utterances via neural encoders spanning headings plus first sentences of stored notes locally.
                    The Android build runs a smaller transformer through ONNX Mobile for pairwise scoring without cloud calls.
                    Extra sentences keep this sample above heuristic thresholds used when suggesting merges between notes apps use.
                    """.trimIndent()
            ),
            Pair(
                    "Grocery reminders",
                    """
                    Almond milk half gallon, lemons, feta cheese crumble, lentils dry bag, quinoa.
                    Bakery aisle sourdough if still warm when we arrive tonight after work commute home again please check.
                    """.trimIndent()
            ),
            Pair(
                    "Groceries tonight",
                    """
                    Grab oat milk carton, lemons for tea, feta for salad, lentils and quinoa staples restock cupboard space.
                    If sourdough is fresh baked pick one loaf commuting back from office weekday evening errands run together.
                    """.trimIndent()
            ),
            Pair(
                    "Thesis methodology outline",
                    """
                    Mixed methods study alternating qualitative interviews with survey instruments across two cohort semesters.
                    IRB approvals pending appendix upload before pilot recruitment begins formally next calendar month timeframe.
                    """.trimIndent()
            ),
            Pair(
                    "Dissertation methods scratchpad",
                    """
                    Using mixed-methods design combining semi-structured interviews plus structured questionnaires longitudinal timing.
                    Expect IRB appendix submission soon then recruit pilot participants spanning following academic semester window broadly.
                    """.trimIndent()
            ),
            Pair(
                    "Running plan week 42",
                    """
                    Tempo intervals Tuesday six by four minutes at lactate pacing with jogging recovery ninety seconds repeats total build.
                    Weekend long progression run climbs gently rolling hills emphasizing conversational pace fueling strategy gel hour mark note.
                    """.trimIndent()
            ),
            Pair(
                    "Running schedule W42 notes",
                    """
                    Interval session Tuesday alternating four minute hardworking segments against relaxed ninety second jogs accumulating volume weekly.
                    Sunday steady long aerobic effort over gentle terrain matching talk-test effort plus carbohydrate timing around sixty minute refill window approximate.
                    """.trimIndent()
            ),
            Pair(
                    "Book club — chapter 9",
                    """
                    Consensus that narrator unreliability peaks during beach sequence symbolism threads water anxiety metaphors cleanly enough discuss next month openly.
                    """.trimIndent()
            ),
            Pair(
                    "Reading group ch.9 reflections",
                    """
                    Everybody agreed unreliable narration intensifies shoreline chapter imagery linking ocean symbolism with creeping dread themes slated continued discussion shortly.
                    """.trimIndent()
            ),
            Pair(
                    "Home Wi-Fi troubleshooting",
                    """
                    Mesh node garage dropped nightly reboot router firmware channel scan forty versus adjacent apartment interference spectrum analyzer someday borrow friend toolkit weekend project slot reserved.
                    """.trimIndent()
            ),
            Pair(
                    "Router mesh garage drops log",
                    """
                    Nightly outages on secondary mesh point near workshop maybe firmware plus crowded five gigahertz band neighbors overlapping channels borrow spectrum tool maybe Saturday afternoon tinkering timeframe window open tentative plan recorded here now again.
                    """.trimIndent()
            ),
            Pair(
                    "Debug filler A",
                    """
                    Alphabet soup paragraph alpha bravo charlie delta echo foxtrot golf hotel india juliet kinetic lamp modular nested opaque protocol queue rhythm signal topic umbrella vector widget xenon yak zebra wrap up words soon enough thankfully yes zone.
                    """.trimIndent()
            ),
            Pair(
                    "Debug filler B",
                    """
                    Sequential filler beta gamma epsilon zeta theta iota lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega trail marker ends paragraph requirement satisfied thankfully complete sentence goodbye.
                    """.trimIndent()
            )
        )
}
