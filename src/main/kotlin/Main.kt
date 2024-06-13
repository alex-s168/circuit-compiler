package me.alex_s168

import blitz.collections.Matrix
import java.io.File

fun Matrix<CoreExecConfig>.allVarUsages(): Sequence<Pair<Int, Pair<Int, Int>>> =
    elementsWithIndexes()
        .flatMap { (x, pos) -> x.exec.flatMap { it.inputs().map { it.node.id to pos } } }

fun main() {
    val file = File("test.json").readText()
    var (net, id) = fromJson(file)

    net.expandOpt(null, listOf(Component.MAKE8, Component.SPLIT8, Component.NAND)) { id ++ }

    net.check()

    val layers = net
        .computeLayers()
        .optimizeLayers(4)
        .layout()

    println(layers)
}
