// The load-high family writes one byte of the accumulator at a time. Build
// 0x12345678 twice and xor the two, which leaves 0.
loadi 0x78
loadhi 0x56
loadh2i 0x34
loadh3i 0x12
store r1
loadi 0x78
loadhi 0x56
loadh2i 0x34
loadh3i 0x12
xor r1

scall 0
