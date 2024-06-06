package me.alex_s168

import blitz.str.splitWithNesting

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
        }

        operator fun set(port: Int, to: WireEnd) {
            set(port, Wire(node, port, to.node, to.port))
        }
    }

    val ports = Ports(this)
}

class ValueNode(
    idIn: Int,
    val value: Boolean
): Node(idIn)

class ComponentNode(
    idIn: Int,
    val component: Component
): Node(idIn)

class InNode(
    idIn: Int,
    val inPortCount: Int
): Node(idIn)

class OutNode(
    idIn: Int,
    val outPortCount: Int
): Node(idIn)

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
        node.wires.forEach { it.kill() }
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
        allWires.forEach { wire ->
            sb.append(wire.toString())
            sb.appendLine()
        }
        return sb.toString()
    }

    fun expand(platformSupportedComponents: List<Component>) {

    }
}

fun main() {
    val code = """
op (!& w w) w =
    : err
    ;

op (! w) w =
    : (!& 0 0)
    ;

op (& w w) w =
    : (! (!& 0 1))
    ;

op (| w w) w =
    : (!& (! 0) (! 1))
    ;

op (!| w w) w =
    : (! (| 0 1))
    ;

op (^ w w) w =
    : (& (| 0 1) (! (& 0 1)))
    ;

op (ha w w) w w =
    : {(^ 0 1) (& 0 1)}
    ;

op (fa w w w) w w =
    : s0, c0 = (ha 0 1)
    : s1, c1 = (ha s0 2)
    : c = (| c0 c1)
    : {s1 c}
    ;

mop (+@T wT wT w) wT w =
    : c, s = foldl@T 2 0 1 # r, it = (fa it 0 1)
    : {s c}
    ;

op (test w8 w8) w8 =
    : z = always@1 b0
    : s, c = (+@8 0 1 z)
    : s
    ;
    """

    val blocks = code
        .replace(Regex("\\R"), "")
        .split(';')
        .mapNotNull {
            val split = it.split('=', limit = 2)
            split.getOrNull(1)?.let { body ->
                val (decl) = split
                val instrs = body.split(':')
                val declParts = decl.splitWithNesting(' ', '(', ')')

                val declKind = declParts[0].trim()
                val declArgs = declParts[1].dropLast(1).drop(1).split(' ').map { it.trim() }
                val declRet = declParts[2].trim()

                instrs.map {
                    val split = it.trim().split('=', limit = 2)
                    val (outs, op) = split.getOrNull(1)?.let {
                        val os = split[0]
                        os.split(',').map { os.trim() } to it
                    } ?: (listOf<String>() to split[0])


                }

                decl to instrs
            }
        }
    println(blocks)
}