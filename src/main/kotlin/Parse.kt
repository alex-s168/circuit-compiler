package me.alex_s168

import blitz.parse.JSON

fun fromJson(str: String): Pair<Net, Int> {
    var id = 0
    val (jwires, jnodes) = JSON.parse(str)!!.obj
        .let { it["wires"]!!.arr to it["nodes"]!!.obj }
    val nodes = jnodes.mapValues {
        val type = it.value.obj["type"]!!.str
        val comp = it.value.obj["components"]?.arr
            ?.map { it.num.toInt() }
            ?.sum()
            ?: 0
        when (type) {
            "Output" -> OutNode(id ++, comp)
            "Input" -> InNode(id ++, comp)
            "Nand" -> ComponentNode(id ++, Component.NAND)
            "Not" -> ComponentNode(id ++, Component.NOT)
            "And" -> ComponentNode(id ++, Component.AND)
            "Or" -> ComponentNode(id ++, Component.OR)
            "Nor" -> ComponentNode(id ++, Component.NOR)
            "Xor" -> ComponentNode(id ++, Component.XOR)
            "Xnor" -> ComponentNode(id ++, Component.XNOR)
            "Splitter8" -> ComponentNode(id ++, Component.SPLIT8)
            "Maker8" -> ComponentNode(id ++, Component.MAKE8)
            else -> error("unsupported")
        }
    }
    jwires.forEach { wire ->
        val width = wire.obj["width"]?.num?.toInt() ?: 1
        val (from, to) = wire.obj
            .let { listOf(it["from"]!!, it["to"]!!) }
            .map { it.arr.let { (node, port) ->
                WireEnd(nodes[node.str]!!, port.num.toInt())
            } }
        from.node.ports[from.port] = Wire(from, to, width)
    }
    val inp = nodes.values.first { it is InNode } as InNode
    val out = nodes.values.first { it is OutNode } as OutNode
    val net = Net(inp, out)
    nodes.values.forEach(net::addNodeRec)
    return net to id
}