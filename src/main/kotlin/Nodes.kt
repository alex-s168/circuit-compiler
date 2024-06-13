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

    SPLIT8(1, 8),
    MAKE8(8, 1),
}

data class Wire(
    val from: Node,
    val fromPort: Int,
    val to: Node,
    val toPort: Int,
    val width: Int = 1,
) {
    constructor(a: WireEnd, b: WireEnd, w: Int = 1):
            this(a.node, a.port, b.node, b.port, w)

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
    val value: Boolean,
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
