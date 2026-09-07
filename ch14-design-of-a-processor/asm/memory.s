// Indirect memory access through the address register AR. AR holds a byte
// address; the immediate is a word offset for `loadind`/`storeind` and a byte
// offset for `loadindb`/`storeindb`.
loadi 0x40
ldaddr
loadi 0x7b
storeind 0
loadi 0x2a
storeind 1
loadind 0
subi 0x7b
brnz fail
loadind 1
subi 0x2a
brnz fail
loadindb 0
subi 0x7b
brnz fail
loadi 0x11
storeindb 1
loadindb 1
subi 0x11

scall 0

fail:
loadi 0xff
scall 0
