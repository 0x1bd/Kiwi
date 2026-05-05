package org.kvxd.kiwi.test

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.kvxd.kiwi.util.PREFIX
import kotlin.system.measureNanoTime

object TestRunner {

    data class TestResult(
        val name: String,
        val passed: Boolean,
        val message: String,
        val durationMs: Double
    )

    private val suites = mutableListOf<TestSuite>()

    fun register(suite: TestSuite) {
        suites.add(suite)
    }

    fun runAll(): List<TestResult> {
        val allResults = mutableListOf<TestResult>()
        for (suite in suites) {
            allResults.addAll(suite.run())
        }
        return allResults
    }

    fun runAndReport(source: net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) {
        val results = runAll()
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val total = results.size

        source.sendFeedback(
            Component.empty()
                .append(PREFIX)
                .append(Component.literal("Tests: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("$total total, ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("$passed passed, ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("$failed failed").withStyle(if (failed > 0) ChatFormatting.RED else ChatFormatting.GREEN))
        )

        for (result in results) {
            val color = if (result.passed) ChatFormatting.GREEN else ChatFormatting.RED
            val symbol = if (result.passed) "✓" else "✗"
            source.sendFeedback(
                Component.empty()
                    .append(Component.literal("  $symbol ${result.name} ").withStyle(color))
                    .append(Component.literal("(${"%.1f".format(result.durationMs)}ms)").withStyle(ChatFormatting.GRAY))
            )
            if (!result.passed) {
                source.sendFeedback(
                    Component.empty()
                        .append(Component.literal("    ${result.message}").withStyle(ChatFormatting.RED))
                )
            }
        }
    }

    open class TestSuite(val name: String) {
        private val tests = mutableListOf<Pair<String, () -> Unit>>()

        fun test(name: String, block: () -> Unit) {
            tests.add(name to block)
        }

        fun run(): List<TestResult> {
            return tests.map { (name, block) ->
                var passed = true
                var message = ""
                val nanos = measureNanoTime {
                    try {
                        block()
                    } catch (e: AssertionError) {
                        passed = false
                        message = e.message ?: "assertion failed"
                    } catch (e: Exception) {
                        passed = false
                        message = "${e.javaClass.simpleName}: ${e.message}"
                    }
                }
                TestResult(name, passed, message, nanos / 1_000_000.0)
            }
        }
    }
}
