.const BLIT_FILL 1

.org 0
start:
    loadi r16, 100
    loadi r17, 200
    mul  r17, r16,r17
    loadi r1, 1
loop:
    blit BLIT_FILL
    addi r16, r16, 1
    sub  r1, r17, r16
    bnez r1, loop

forever:
    jump forever
