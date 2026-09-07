// All five branches. A counting loop on brnz, then brz, brn, br, and brp in
// turn; any wrong branch lands on a `loadi 0xff` and fails the accumulator
// check at the end.
loadi 5
loop:
subi 1
brnz loop
brz cont
loadi 0xff
scall 0

cont:
subi 1
brn neg
loadi 0xff
scall 0

neg:
addi 1
br done
loadi 0xff

done:
brp ok
loadi 0xff

ok:
scall 0
