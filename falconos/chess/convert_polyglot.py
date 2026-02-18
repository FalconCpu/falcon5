import struct

# Read all entries first to count them
entries = []
with open('../../../Downloads/rodent.bin', 'rb') as f_in:
    while True:
        entry = f_in.read(16)  # PolyGlot entry
        if not entry: break
        
        # Unpack big-endian
        hash_be = struct.unpack('>Q', entry[0:8])[0]   # > = big-endian, Q = uint64
        move_be = struct.unpack('>H', entry[8:10])[0]  # H = uint16
        weight_be = struct.unpack('>H', entry[10:12])[0]
        learn_be = struct.unpack('>I', entry[12:16])[0] # I = uint32
        
        entries.append((hash_be, move_be, weight_be, learn_be))

# Write with count header for FPL array bounds checking
with open('book_le.bin', 'wb') as f_out:
    # Write array length as 4-byte little-endian int (stored at array-4)
    f_out.write(struct.pack('<I', len(entries)))
    
    # Write all entries in little-endian format
    for hash_val, move_val, weight_val, learn_val in entries:
        f_out.write(struct.pack('<Q', hash_val))    # < = little-endian
        f_out.write(struct.pack('<H', move_val))
        f_out.write(struct.pack('<H', weight_val))
        f_out.write(struct.pack('<I', learn_val))

print(f"Converted {len(entries)} opening book entries from big-endian to little-endian")