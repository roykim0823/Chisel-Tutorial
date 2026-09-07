// Register operands. The registers are just words in the data memory, so
// `store rn` and `load rn`, `add rn` and the rest address that memory with
// the instruction's low byte.
loadi 0x0f
store r1
loadi 0x33
store r2
load r1
and r2
store r3
load r1
or r2
xor r2
sub r3
add r3
shr
shr
sub r3

scall 0
