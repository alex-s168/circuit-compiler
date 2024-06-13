package me.alex_s168

import blitz.collections.Matrix
import java.io.File

fun Matrix<CoreExecConfig>.allVarUsages(): Sequence<Pair<Int, Pair<Int, Int>>> =
    elementsWithIndexes()
        .flatMap { (x, pos) -> x.exec.flatMap { it.inputs().map { it.node.id to pos } } }

object Comp {
    val nodeWidth = 1L + 2L * 9 // byte make / split

    const val NODE_NOP = 0
    const val NODE_INPUT = 1
    const val NODE_OUTPUT = 2
    const val NODE_MAKE8 = 3
    const val NODE_SPLIT8 = 4
    const val NODE_NAND = 5

    fun nodeInfoInto(arr: ByteArray, arrOff: Int, type: Int, hashNext: Boolean) {
        val res = (type shl 1) or (if (hashNext) 1 else 0)
        arr[arrOff] = res.toByte()
    }

    fun addrInto(arr: ByteArray, arrOff: Int, addr: Int, global: Boolean) {
        val res = (addr shl 1) or (if (global) 1 else 0)
        arr[arrOff] = ((res shr 8) and 0xFF).toByte()
        arr[arrOff + 1] = (res and 0xFF).toByte()
    }
}

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

    runCatching {
        val dev = GPU.devices.first()
        println("selected device: $dev")
    }.onFailure {
        println("failed to select compute device")
    }
}
