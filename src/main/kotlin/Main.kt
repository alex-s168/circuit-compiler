package me.alex_s168

import blitz.collections.Matrix
import blitz.collections.contents
import blitz.logic.then
import blitz.str.MutMultiLineString

enum class Component(val inPortCount: Int, val outPortCount: Int) {
    NAND(2, 1),
    NOT(1, 1),
    AND(2, 1),
    OR(2, 1),
    NOR(2, 1),
    XOR(2, 1),
    XNOR(2, 1),

    HALF_ADD(2, 2),
    FULL_ADD(3, 2),
}

data class Wire(
    val from: Node,
    val fromPort: Int,
    val to: Node,
    val toPort: Int
) {
    var dead = false
        private set

    override fun toString() =
        "${from.id}[$fromPort] <-> ${to.id}[$toPort]"

    fun kill() {
        assert(!dead)
        dead = true
        from.wires.remove(this)
        to.wires.remove(this)
    }

    fun swap() =
        Wire(to, toPort, from, fromPort)

    fun from() =
        WireEnd(from, fromPort)

    fun to() =
        WireEnd(to, toPort)
}

data class WireEnd(
    val node: Node,
    val port: Int
) {
    override fun toString() =
        "${node.id}[$port]"
}

abstract class Node(val id: Int) {
    var wires = mutableSetOf<Wire>()

    override fun hashCode(): Int =
        id.hashCode()

    override fun equals(other: Any?) =
        other is Node && other.id == id

    class Ports internal constructor(private val node: Node) {
        operator fun get(port: Int): List<Wire> =
            node.wires.filter {
                (it.from == node && it.fromPort == port) ||
                (it.to == node && it.toPort == port)
            }

        operator fun set(port: Int, wire: Wire) {
            // shooould check here
            node.wires.add(wire)

            // add to other node
            if (wire.to == node) {
                wire.from.wires.add(wire)
            } else {
                wire.to.wires.add(wire)
            }
        }

        operator fun set(port: Int, to: WireEnd?) {
            to?.let {
                set(port, Wire(node, port, it.node, it.port))
            }
        }

        fun out(port: Int) =
            node.ports[port].map { if (it.to == node) it.swap() else it }

        fun inp(port: Int) =
            node.ports[port].firstOrNull()?.let { if (it.from == node) it.swap() else it }
    }

    val ports = Ports(this)

    open fun inputs(): List<WireEnd> =
        emptyList()

    open fun outputs(): List<WireEnd> =
        inputs().let { inp ->
            wires.filter { it.from() !in inp && it.to() !in inp }
        }
        .map { if (it.from == this) it.from() else it.to() }
}

class ValueNode(
    idIn: Int,
    val value: Boolean
): Node(idIn) {
    override fun toString() =
        "$id = value($value)"
}

class ComponentNode(
    idIn: Int,
    val component: Component
): Node(idIn) {
    override fun toString() =
        "$id = $component(${inputs().joinToString()})"

    override fun inputs() = wires
        .map { if (it.to == this) it else it.swap() }
        .filter { it.toPort < this.component.inPortCount }
        .map { it.from() }
}

class InNode(
    idIn: Int,
    val inPortCount: Int
): Node(idIn) {
    override fun toString() =
        "$id = input"
}

class OutNode(
    idIn: Int,
    val outPortCount: Int
): Node(idIn) {
    override fun toString() =
        "$id = output(${inputs().joinToString()})"

    override fun inputs() = wires
        .map { if (it.to == this) it.from() else it.to() }
}

class Net(inputIn: InNode, outputIn: OutNode) {
    var input = inputIn
        set(value) {
            killNode(field)
            field = value
            addNodeRec(field)
        }

    var output = outputIn
        set(value) {
            killNode(field)
            field = value
            addNodeRec(field)
        }

    fun killNode(node: Node) {
        allNodes.remove(node)
        node.wires.toList().forEach { it.kill() }
    }

    fun addNodeRec(node: Node) {
        if (node !in allNodes) {
            allNodes.add(node)
            allWires.addAll(node.wires)
            node.wires.forEach {
                addNodeRec(it.to)
                addNodeRec(it.from)
            }
        }
    }

    private var allNodes = mutableSetOf(input, output)
    private var allWires = mutableSetOf<Wire>()
        get() {
            field.removeAll { it.dead }
            return field
        }

    fun allNodesIter(): Iterable<Node> =
        allNodes

    fun allWiresIter(): Iterable<Wire> =
        allWires

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("nodes:\n")
        allNodes.forEach { node ->
            sb.append("  ")
            sb.append(node.toString())
            sb.appendLine()
        }
        return sb.toString()
    }

    fun expandOpt(from: Component?, platformSupportedComponents: List<Component>, newId: () -> Int) {
        while (allNodes.any { it is ComponentNode && it.component !in platformSupportedComponents }) {
            expandIter(from, platformSupportedComponents, newId)
        }
    }

    fun expandIter(from: Component?, platformSupportedComponents: List<Component>, newId: () -> Int) {
        val kill = mutableListOf<Node>()
        val add = mutableListOf<Node>()
        for (node in allNodes) {
            if (node is ComponentNode) {
                if (node.component !in platformSupportedComponents && (from ?: node) == node) {
                    kill.add(node)
                    when (node.component) {
                        Component.NOT -> {
                            val i = node.ports.inp(0)!!
                            val o = node.ports.out(1)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i.from()
                            nand.ports[1] = i.from()

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.AND -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0.from()
                            nand.ports[1] = i1.from()

                            add.add(nand)

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(nand, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.NAND -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = i0!!.from()
                            and.ports[1] = i1!!.from()

                            add.add(and)

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(and, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.XOR -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0.from()
                            nand.ports[1] = i1.from()

                            add.add(nand)

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = i0.from()
                            or.ports[1] = i1.from()

                            add.add(or)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = WireEnd(nand, 2)
                            and.ports[1] = WireEnd(or, 2)

                            o.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.OR -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val o = node.ports.out(2)

                            val not0 = ComponentNode(newId(), Component.NOT)
                            not0.ports[0] = i0.from()

                            add.add(not0)

                            val not1 = ComponentNode(newId(), Component.NOT)
                            not1.ports[0] = i1.from()

                            add.add(not1)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = WireEnd(not0, 1)
                            nand.ports[1] = WireEnd(not1, 1)

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.HALF_ADD -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val sum = node.ports.out(2)
                            val carr = node.ports.out(3)

                            val xor = ComponentNode(newId(), Component.XOR)
                            xor.ports[0] = i0.from()
                            xor.ports[1] = i1.from()

                            sum.forEach {
                                xor.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(xor)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = i0.from()
                            and.ports[1] = i1.from()

                            carr.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.FULL_ADD -> {
                            val i0 = node.ports.inp(0)!!
                            val i1 = node.ports.inp(1)!!
                            val i2 = node.ports.inp(2)!!
                            val sum = node.ports.out(3)
                            val carr = node.ports.out(4)

                            val ha0 = ComponentNode(newId(), Component.HALF_ADD)
                            ha0.ports[0] = i0.from()
                            ha0.ports[1] = i1.from()

                            add.add(ha0)

                            val ha1 = ComponentNode(newId(), Component.HALF_ADD)
                            ha1.ports[0] = i2.from()
                            ha1.ports[1] = WireEnd(ha0, 2)

                            sum.forEach {
                                ha1.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(ha1)

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = WireEnd(ha0, 3)
                            or.ports[1] = WireEnd(ha1, 3)

                            carr.forEach {
                                or.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(or)
                        }

                        else -> error("don't know how to expand ${node.component} to $platformSupportedComponents")
                    }
                    break
                }
            }
        }
        kill.forEach {
            killNode(it)
        }
        add.forEach {
            addNodeRec(it)
        }
    }

    fun check() {
        allNodes.forEach { node ->
            when (node) {
                is ComponentNode -> {
                    require(node.inputs().size == node.component.inPortCount) {
                        "failed on $node"
                    }
                }
            }
        }
    }

    fun computeLayers(): List<List<Node>> {
        val layers = mutableListOf<List<Node>>()
        val remNodes = allNodes.toMutableList()

        while (remNodes.isNotEmpty()) {
            val layer = mutableListOf<Node>()

            remNodes.toList().forEach { node ->
                val inputs = node.inputs()

                if (inputs.all { i -> layers.any { i.node in it } }) {
                    remNodes.remove(node)
                    layer.add(node)
                }
            }

            if (layer.isEmpty() && remNodes.isNotEmpty()) {
                error("recursive dependency detected in ${remNodes.contents}")
            }

            layers.add(layer)
        }

        return layers
    }
}

data class CoreExecConfig(
    val exec: MutableList<Node>
) {
    override fun toString() =
        exec.joinToString("\n")
}

fun List<List<Node>>.optimizeLayers(maxUnitCompute: Int): Matrix<CoreExecConfig> {
    val mat = Matrix(maxOf { it.size }, size) { _, _ -> CoreExecConfig(mutableListOf()) }

    forEachIndexed { row, nodes ->
        nodes.forEach { node ->
            val deps = node.inputs()
            val placed = mat
                .elementsWithIndexes()
                .find {
                    (node) -> deps.any { dep ->
                        node.exec.any {
                            it.wires.any { it.from() == dep || it.to() == dep }
                        }
                    }
                }
                ?.let { (_, loc) ->
                    (mat[loc.first, row].exec.size < maxUnitCompute).then {
                        mat[loc.first, row].exec.add(node)
                    }
                }
                ?: false
            if (!placed) {
                val col = mat.rows[row].indexOfFirst { it.exec.size < maxUnitCompute }
                mat[col, row].exec.add(node)
            }
        }
    }

    return mat
}

data class CoreConfig(
    val localNodes: MutableSet<Int>
) {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Core:")
        localNodes.forEach { v ->
            sb.appendLine("local $v")
        }
        return sb.toString()
    }
}

data class Layout(
    val configs: List<CoreConfig>,
    val layers: Matrix<CoreExecConfig>,
    val globals: MutableSet<Int>,
) {
    init {
        require(configs.size == layers.width)
    }

    override fun toString(): String {
        val mat = Matrix(layers.width, layers.height + 1) { x, y ->
            if (y == 0) configs[x].toString()
            else layers[x, y - 1].toString()
        }

        val sb = StringBuilder()
        sb.appendLine("LAYOUT")
        sb.appendLine("globals: ${globals.joinToString()}\n")
        sb.append(mat)

        return sb.toString()
    }
}

fun Matrix<CoreExecConfig>.allVarUsages(): Sequence<Pair<Int, Pair<Int, Int>>> =
    elementsWithIndexes()
        .flatMap { (x, pos) -> x.exec.flatMap { it.inputs().map { it.node.id to pos } } }

fun Matrix<CoreExecConfig>.layout(): Layout {
    val tra = transposeCopy()

    val allVarsPerCore = tra.rows.map { it.flatMap { it.exec.map { it.id } } }
    val allVarUsages = allVarUsages()

    val global = mutableSetOf<Int>()
    val perCore = allVarsPerCore.mapIndexed { col, vars ->
        val local = mutableSetOf<Int>()
        vars.forEach { v ->
            val usages = allVarUsages
                .filter { it.first == v }
            if (usages.any { it.second.first != col })
                global.add(v)
            else
                local.add(v)
        }
        CoreConfig(local)
    }

    return Layout(
        perCore,
        this,
        global
    )
}

fun main() {
    var id = 0

    val inp = InNode(id ++, 2)
    val out = OutNode(id ++, 1)
    val net = Net(inp, out)

    val zero = ValueNode(id ++, false)
    val fa = ComponentNode(id ++, Component.FULL_ADD)
    fa.ports[0] = WireEnd(inp, 0)
    fa.ports[1] = WireEnd(inp, 1)
    fa.ports[2] = WireEnd(zero, 0)

    out.ports[0] = WireEnd(fa, 3)

    net.addNodeRec(fa)

    net.expandOpt(null, listOf(Component.NAND)) { id ++ }

    net.check()

    val layers = net
        .computeLayers()
        .optimizeLayers(4)
        .layout()

    println(layers)
}