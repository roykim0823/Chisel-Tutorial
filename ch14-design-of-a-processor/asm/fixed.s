// The hand-encoded program of Section 14.6, in assembly. It is assembled by
// AssemblerTest and compared against that hard-coded array; it does not end
// with a system call, so it is not meant to be run.
addi 0x3
addi 0xff
subi 2
loadi 0xab
andi 0x0f
ori 0xc3
nop
