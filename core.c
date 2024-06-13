// NUM_LOCAL_WIRES, NUM_NODE_BYTES, NUM_LAYERS, NUM_LAYER_NODES

typedef uchar uint8_t;
typedef ushort uint16_t;
typedef uint uint32_t;

#define WIREID_NONE 0
typedef uint16_t wire_id;

#define core_id get_local_id(0)

// do I need __global here?
static uint8_t load_wire(local uint8_t * private local_wires, global uint8_t *global_wires, wire_id wire) {
  uint16_t addr = wire >> 1;
  if (wire & 1)
    return global_wires[addr];
  else
    return local_wires[addr];
}

static void store_wire(local uint8_t * private local_wires, global uint8_t *global_wires, wire_id wire, uint8_t value) {
  uint16_t addr = wire >> 1;
  if (wire & 1)
    global_wires[addr] = value;
  else
    local_wires[addr] = value;
}

typedef enum {
  tNOP = 0,
  tINPUT = 1,
  tOUTPUT = 2,
  tMAKE8 = 3,
  tSPLIT8 = 4,
  tNAND = 5,
} node_type;

kernel void compute(global  uint8_t *global_wires,
                    global  uint8_t *in_wires,
                    global  uint8_t *out_wires,
                    global  uint8_t *nodes,
                    local   uint8_t *local_wires_block)
{
  local uint8_t * private local_wires = local_wires_block + (NUM_LOCAL_WIRES * get_local_id(0));

  for (size_t layer = 0; layer < NUM_LAYERS; layer ++) {
    global uint8_t *dp = nodes + (layer * NUM_LAYER_NODES + core_id) * NUM_NODE_BYTES;
    bool next = false;
    do {
      uint8_t cfg = *(dp ++);
      next = cfg & 1;
      node_type type = cfg >> 1;
      switch (type) {
        case tNOP:
          break;

        case tINPUT: {
          uint8_t in_off = *(dp ++);
          for (size_t i = 0; i < 4; i ++) {
            wire_id dst = *(global wire_id*)dp;
            dp += 2;
            if (dst == WIREID_NONE)
              break;
            store_wire(local_wires, global_wires, dst, in_wires[in_off ++]);
          }
        } break;

        case tOUTPUT: {
          uint8_t out_off = *(dp ++);
          for (size_t i = 0; i < 4; i ++) {
            wire_id src = *(global wire_id*)dp;
            dp += 2;
            if (src == WIREID_NONE)
              break;
            out_wires[out_off ++] = load_wire(local_wires, global_wires, src);
          }
        } break;

        case tMAKE8: {
          uint8_t res = 0;
          for (size_t i = 0; i < 8; i ++) {
            wire_id src = *(global wire_id*)dp;
            dp += 2;
            uint8_t val = load_wire(local_wires, global_wires, src);
            res <<= 1;
            res |= val & 1;
          }
          wire_id dst = *(global wire_id*)dp;
          dp += 2;
          store_wire(local_wires, global_wires, dst, res);
        } break;

        case tSPLIT8: {
          wire_id src = *(global wire_id*)dp;
          dp += 2;
          uint8_t val = load_wire(local_wires, global_wires, src);

          for (size_t i = 0; i < 8; i ++) {
            uint8_t x = val & 1;
            val >>= 1;

            wire_id dst = *(global wire_id*)dp;
            dp += 2;
            store_wire(local_wires, global_wires, dst, x);
          }
        } break;

        case tNAND: {
          wire_id src0 = *(global wire_id*)dp;
          dp += 2;
          uint8_t v0 = load_wire(local_wires, global_wires, src0);

          wire_id src1 = *(global wire_id*)dp;
          dp += 2;
          uint8_t v1 = load_wire(local_wires, global_wires, src1);

          uint8_t res = ~(v0 & v1);

          wire_id dst = *(global wire_id*)dp;
          dp += 2;
          store_wire(local_wires, global_wires, dst, res);
        } break;
      }
    } while (next);
    barrier(CLK_LOCAL_MEM_FENCE | CLK_GLOBAL_MEM_FENCE); // maybe can be changed
  }
}
