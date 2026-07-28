package org.kvxd.kiwi.test.suites

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.Kiwi
import org.kvxd.kiwi.path.BreakPolicy
import org.kvxd.kiwi.path.MoveBuffer
import org.kvxd.kiwi.path.MoveGenerator
import org.kvxd.kiwi.path.MoveKind
import org.kvxd.kiwi.path.NO_BREAKS
import org.kvxd.kiwi.path.NO_PLACE
import org.kvxd.kiwi.path.PathContext
import org.kvxd.kiwi.path.PathNode
import org.kvxd.kiwi.test.BotTestWorld
import org.kvxd.kiwi.test.check
import org.kvxd.kiwi.test.fromClient
import org.kvxd.kiwi.test.onClient
import org.kvxd.kiwi.test.checkClose
import org.kvxd.kiwi.world.PlayerBox
import org.kvxd.kiwi.world.ShapeKind
import org.kvxd.kiwi.world.Stances

object VoxelSuite {

    fun run(world: BotTestWorld) {
        val origin = world.arena(sizeX = 24, sizeZ = 24)
        val floor = origin.y
        val stand = floor + 1

        supportHeights(world, origin, floor, stand)
        fenceGeometry(world, origin, floor, stand)
        stepRules(world, origin, floor, stand)
        oneBlockGaps(world, origin, floor, stand)
        overhangTransitions(world, origin, floor, stand)
        breakThroughObstacles(world, origin, floor, stand)
    }

    private fun supportHeights(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val fullBlock = BlockPos(origin.x + 2, stand, origin.z + 2)
        val bottomSlab = BlockPos(origin.x + 4, stand, origin.z + 2)
        val topSlab = BlockPos(origin.x + 6, stand, origin.z + 2)
        val stairs = BlockPos(origin.x + 8, stand, origin.z + 2)
        val carpet = BlockPos(origin.x + 10, stand, origin.z + 2)

        world.setBlock(fullBlock, "minecraft:stone")
        world.setBlock(bottomSlab, "minecraft:stone_slab[type=bottom]")
        world.setBlock(topSlab, "minecraft:stone_slab[type=top]")
        world.setBlock(stairs, "minecraft:stone_stairs[facing=east,half=bottom,shape=straight]")
        world.setBlock(carpet, "minecraft:white_carpet")
        world.settle(6)

        world.withWorldView { view, _ ->
            val onFloor = Stances.standingFeetHeight(view, origin.x + 1, stand, origin.z + 2)
            checkClose(stand.toDouble(), onFloor, 1.0E-6) { "flat ground stance" }

            val onFullBlock = Stances.standingFeetHeight(view, fullBlock.x, fullBlock.y + 1, fullBlock.z)
            checkClose((stand + 1).toDouble(), onFullBlock, 1.0E-6) { "stance on a full block" }

            val onBottomSlab = Stances.standingFeetHeight(view, bottomSlab.x, bottomSlab.y, bottomSlab.z)
            checkClose(stand + 0.5, onBottomSlab, 1.0E-6) { "stance on a bottom slab must be half a block up" }

            check(!Stances.isValid(Stances.standingFeetHeight(view, topSlab.x, topSlab.y, topSlab.z))) {
                "a top slab leaves no room to stand inside its own cell"
            }
            val aboveTopSlab = Stances.standingFeetHeight(view, topSlab.x, topSlab.y + 1, topSlab.z)
            checkClose((stand + 1).toDouble(), aboveTopSlab, 1.0E-6) { "stance on top of a top slab" }

            val onStairs = Stances.standingFeetHeight(view, stairs.x, stairs.y + 1, stairs.z)
            checkClose((stand + 1).toDouble(), onStairs, 1.0E-6) {
                "a centred player overlaps the raised half of a stair and rests at full height"
            }

            val carpetProfile = view.profile(carpet)
            val onCarpet = Stances.standingFeetHeight(view, carpet.x, carpet.y, carpet.z)
            check(Stances.isValid(onCarpet)) { "carpet must be standable ${describe(view, carpet)}" }
            check(onCarpet > stand && onCarpet < stand + 0.2) {
                "carpet support should be a sliver above the floor, got $onCarpet ${describe(view, carpet)}"
            }
            check(carpetProfile.supportTop < 0.2) { "carpet is only a sliver tall ${describe(view, carpet)}" }

            val slabProfile = view.profile(bottomSlab)
            check(slabProfile.shapeKind == ShapeKind.PARTIAL) { "a slab is not a full cube" }
            checkClose(0.5, slabProfile.supportTop, 1.0E-6) { "slab support height" }

            val stoneProfile = view.profile(fullBlock)
            check(stoneProfile.shapeKind == ShapeKind.FULL_CUBE) { "stone is a full cube" }
        }
    }

    private fun fenceGeometry(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val fence = BlockPos(origin.x + 2, stand, origin.z + 6)
        world.setBlock(fence, "minecraft:oak_fence")
        world.settle(4)

        world.withWorldView { view, _ ->
            val profile = view.profile(fence)
            check(profile.supportTop > 1.4) {
                "a fence is 1.5 blocks tall for collision, got supportTop=${profile.supportTop}"
            }

            val onFence = Stances.standingFeetHeight(view, fence.x, fence.y + 1, fence.z)
            checkClose(stand + 1.5, onFence, 1.0E-6) { "standing on a fence post" }

            val from = PathNode(
                fence.x - 1, stand, fence.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val moves = generate(view, from, BreakPolicy.NEVER)
            val ontoFence = (0 until moves.size).any { i ->
                moves.x[i] == fence.x && moves.z[i] == fence.z && moves.feetY[i] > stand + 1.0
            }
            check(!ontoFence) { "the planner must not think it can hop a fence (1.5 > ${PlayerBox.JUMP_HEIGHT})" }
        }
    }

    private fun stepRules(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val slabStep = BlockPos(origin.x + 2, stand, origin.z + 10)
        val fullStep = BlockPos(origin.x + 6, stand, origin.z + 10)
        world.setBlock(slabStep, "minecraft:stone_slab[type=bottom]")
        world.setBlock(fullStep, "minecraft:stone")
        world.settle(4)

        world.withWorldView { view, _ ->
            val beforeSlab = PathNode(
                slabStep.x - 1, stand, slabStep.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val slabMoves = generate(view, beforeSlab, BreakPolicy.NEVER)
            val ontoSlab = (0 until slabMoves.size).firstOrNull { i ->
                slabMoves.x[i] == slabStep.x && slabMoves.z[i] == slabStep.z
            }
            check(ontoSlab != null) { "the planner must be able to step onto a slab" }
            check(slabMoves.kind[ontoSlab!!] == MoveKind.WALK) {
                "a 0.5 rise is an auto-step, not a jump (got ${slabMoves.kind[ontoSlab]})"
            }
            checkClose(stand + 0.5, slabMoves.feetY[ontoSlab], 1.0E-6) { "slab step lands half a block up" }

            val beforeFull = PathNode(
                fullStep.x - 1, stand, fullStep.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val fullMoves = generate(view, beforeFull, BreakPolicy.NEVER)
            val ontoFull = (0 until fullMoves.size).firstOrNull { i ->
                fullMoves.x[i] == fullStep.x && fullMoves.z[i] == fullStep.z && fullMoves.feetY[i] > stand + 0.5
            }
            check(ontoFull != null) { "the planner must be able to jump onto a full block" }
            check(fullMoves.kind[ontoFull!!] == MoveKind.JUMP) {
                "a 1.0 rise needs a jump (got ${fullMoves.kind[ontoFull]})"
            }
        }

        Kiwi.logger.info("Kiwi test: voxel stance rules verified")
    }

    private fun oneBlockGaps(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val pocket = BlockPos(origin.x + 2, stand, origin.z + 14)

        world.fill(
            BlockPos(pocket.x - 1, stand, pocket.z - 1),
            BlockPos(pocket.x + 1, stand + 1, pocket.z + 1),
            "minecraft:stone"
        )
        world.setBlock(pocket, "minecraft:air")
        world.settle(6)

        world.withWorldView { view, _ ->
            check(!Stances.isValid(Stances.standingFeetHeight(view, pocket.x, pocket.y, pocket.z))) {
                "a one block tall pocket is not standable ${describe(view, pocket)}"
            }
            check(!Stances.hasClearance(view, pocket.x, pocket.z, pocket.y.toDouble())) {
                "a one block tall pocket has no body clearance ${describe(view, pocket)}"
            }

            for (dx in intArrayOf(-1, 1)) {
                val from = PathNode(
                    pocket.x + dx * 2, stand + 1, pocket.z, (stand + 1).toDouble(),
                    MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
                )
                val moves = generate(view, from, BreakPolicy.NEVER)
                val entersPocket = (0 until moves.size).any { i ->
                    moves.x[i] == pocket.x && moves.y[i] == pocket.y && moves.z[i] == pocket.z
                }
                check(!entersPocket) { "the planner must not walk into a one block gap ${describe(view, pocket)}" }
            }
        }

        Kiwi.logger.info("Kiwi test: one block gaps rejected by stance and successor generation")
    }

    private fun overhangTransitions(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val niche = BlockPos(origin.x + 6, stand - 1, origin.z + 18)

        world.fill(
            BlockPos(niche.x - 1, stand - 1, niche.z - 1),
            BlockPos(niche.x + 1, stand - 1, niche.z + 1),
            "minecraft:air"
        )
        world.setBlock(BlockPos(niche.x, stand + 1, niche.z), "minecraft:stone")
        world.settle(6)

        world.withWorldView { view, _ ->
            val nicheStance = Stances.standingFeetHeight(view, niche.x, niche.y, niche.z)
            check(Stances.isValid(nicheStance)) { "the niche itself is standable ${describe(view, niche)}" }

            val from = PathNode(
                niche.x - 2, stand, niche.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val moves = generate(view, from, BreakPolicy.NEVER)
            val steppedUnderOverhang = (0 until moves.size).any { i ->
                moves.x[i] == niche.x && moves.z[i] == niche.z && moves.y[i] == niche.y
            }
            check(!steppedUnderOverhang) {
                "stepping down into a column whose head level is blocked would walk into the overhang " +
                    describe(view, BlockPos(niche.x, stand + 1, niche.z))
            }
        }

        Kiwi.logger.info("Kiwi test: step transitions respect head level obstructions")
    }

    private fun breakThroughObstacles(world: BotTestWorld, origin: BlockPos, floor: Int, stand: Int) {
        val headOnly = BlockPos(origin.x + 14, stand + 1, origin.z + 2)
        val wallFoot = BlockPos(origin.x + 18, stand, origin.z + 2)

        world.setBlock(headOnly, "minecraft:stone")
        world.setBlock(wallFoot, "minecraft:stone")
        world.setBlock(wallFoot.above(), "minecraft:stone")
        world.settle(6)

        world.withWorldView { view, _ ->
            val beforeHead = PathNode(
                headOnly.x - 1, stand, headOnly.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val headMoves = generate(view, beforeHead, BreakPolicy.ANY)
            val headBreak = (0 until headMoves.size).firstOrNull { i ->
                headMoves.x[i] == headOnly.x && headMoves.z[i] == headOnly.z && headMoves.y[i] == stand
            }
            check(headBreak != null) {
                "the planner must offer a way into a column blocked only at head level " +
                    describe(view, headOnly)
            }
            val headBreaks = headMoves.breaks[headBreak!!] ?: NO_BREAKS
            check(headBreaks.contains(headOnly.asLong())) {
                "entering that column requires breaking the head level block, got ${headBreaks.size} breaks"
            }

            val beforeWall = PathNode(
                wallFoot.x - 1, stand, wallFoot.z, stand.toDouble(),
                MoveKind.WALK, NO_BREAKS, NO_PLACE, 0, 0, 0.0, 0.0, null
            )
            val wallMoves = generate(view, beforeWall, BreakPolicy.ANY)
            val wallBreak = (0 until wallMoves.size).firstOrNull { i ->
                wallMoves.x[i] == wallFoot.x && wallMoves.z[i] == wallFoot.z && wallMoves.y[i] == stand
            }
            check(wallBreak != null) { "the planner must offer to tunnel through a two block wall" }
            val wallBreaks = wallMoves.breaks[wallBreak!!] ?: NO_BREAKS
            check(wallBreaks.size == 2) { "tunnelling a two block wall breaks both cells, got ${wallBreaks.size}" }
        }

        Kiwi.logger.info("Kiwi test: break-through moves offered for head level and full walls")
    }

    private fun describe(view: org.kvxd.kiwi.world.WorldView, pos: BlockPos): String {
        val profile = view.profile(pos)
        val below = view.profile(pos.below())
        val name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(profile.state.block)
        val belowName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(below.state.block)
        return "[block=$name known=${profile.known} kind=${profile.shapeKind} " +
            "supportTop=${profile.supportTop} spans=${profile.footprintSpans.joinToString(",")} " +
            "below=$belowName/${below.shapeKind}/${below.supportTop}]"
    }

    private fun generate(
        view: org.kvxd.kiwi.world.WorldView,
        from: PathNode,
        breakPolicy: BreakPolicy
    ): MoveBuffer {
        val context = PathContext(view = view, breakPolicy = breakPolicy, allowPlace = false)
        val buffer = MoveBuffer()
        MoveGenerator(context).generate(from, buffer)
        return buffer
    }
}
