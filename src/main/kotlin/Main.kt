package me.alex_s168

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

data class WireEnd(val node: Node, val port: Int)

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
        "$id = $component"
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
        "$id = output"
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
        sb.append("wires:\n")
        allWires.forEach { wire ->
            sb.append("  ")
            sb.append(wire.toString())
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
        allNodes.forEach { node ->
            if (node is ComponentNode) {
                if (node.component !in platformSupportedComponents && (from ?: node) == node) {
                    kill.add(node)
                    when (node.component) {
                        Component.NOT -> {
                            val i = node.ports.inp(0)
                            val o = node.ports.out(1)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i?.from()
                            nand.ports[1] = i?.from()

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.AND -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0?.from()
                            nand.ports[1] = i1?.from()

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
                            and.ports[0] = i0?.from()
                            and.ports[1] = i1?.from()

                            val not = ComponentNode(newId(), Component.NOT)
                            not.ports[0] = WireEnd(and, 2)

                            o.forEach {
                                not.ports[1] = WireEnd(it.to, it.toPort)
                            }

                            add.add(not)
                        }

                        Component.XOR -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = i0?.from()
                            nand.ports[1] = i1?.from()

                            val or = ComponentNode(newId(), Component.OR)
                            or.ports[0] = i0?.from()
                            or.ports[1] = i1?.from()

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = WireEnd(nand, 2)
                            and.ports[1] = WireEnd(or, 2)

                            o.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.OR -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val o = node.ports.out(2)

                            val not0 = ComponentNode(newId(), Component.NOT)
                            not0.ports[0] = i0?.from()

                            val not1 = ComponentNode(newId(), Component.NOT)
                            not1.ports[0] = i1?.from()

                            val nand = ComponentNode(newId(), Component.NAND)
                            nand.ports[0] = WireEnd(not0, 1)
                            nand.ports[1] = WireEnd(not0, 1)

                            o.forEach {
                                nand.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(nand)
                        }

                        Component.HALF_ADD -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val sum = node.ports.out(2)
                            val carr = node.ports.out(3)

                            val xor = ComponentNode(newId(), Component.XOR)
                            xor.ports[0] = i0?.from()
                            xor.ports[1] = i1?.from()

                            sum.forEach {
                                xor.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(xor)

                            val and = ComponentNode(newId(), Component.AND)
                            and.ports[0] = i0?.from()
                            and.ports[1] = i1?.from()

                            carr.forEach {
                                and.ports[2] = WireEnd(it.to, it.toPort)
                            }

                            add.add(and)
                        }

                        Component.FULL_ADD -> {
                            val i0 = node.ports.inp(0)
                            val i1 = node.ports.inp(1)
                            val i2 = node.ports.inp(2)
                            val sum = node.ports.out(3)
                            val carr = node.ports.out(4)

                            val ha0 = ComponentNode(newId(), Component.HALF_ADD)
                            ha0.ports[0] = i0?.from()
                            ha0.ports[1] = i1?.from()

                            val ha1 = ComponentNode(newId(), Component.HALF_ADD)
                            ha1.ports[0] = i2?.from()
                            ha1.ports[1] = WireEnd(ha0, 2)

                            sum.forEach {
                                ha1.ports[2] = WireEnd(it.to, it.toPort)
                            }

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

    println(net)
    println()

    net.expandOpt(null, listOf(Component.NAND)) { id ++ }

    println(net)
}