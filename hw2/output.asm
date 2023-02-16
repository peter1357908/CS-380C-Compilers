%include "io.inc"

section .text
	global CMAIN
CMAIN:
	push ebp
	mov ebp, esp

	call mainBali

	PRINT_DEC 4, eax
	NEWLINE

	pop ebp
	ret
method1_0:
push ebp
mov ebp, esp
push dword 0
mov eax, 0
mov [ebp-4], eax
while_start_2:
mov eax, [ebp+8]
push eax
mov eax, 0
pop ebx
cmp ebx, eax
jg true_4
mov eax, 0
jmp post_conditional_5
true_4:
mov eax, 1
post_conditional_5:
cmp eax, 0
jne true_6
jmp break_3
jmp post_conditional_7
true_6:
mov eax, [ebp+8]
push eax
mov eax, 10
pop ebx
add eax, ebx
mov [ebp-4], eax
mov eax, [ebp+8]
push eax
mov eax, 1
pop ebx
sub ebx, eax
mov eax, ebx
mov [ebp+8], eax
jmp break_3
jmp while_start_2
post_conditional_7:
break_3:
mov eax, [ebp-4]
jmp method1_end_1
method1_end_1:
add esp, 4
pop ebp
ret
mainBali:
push ebp
mov ebp, esp
mov eax, 5
push eax
call method1_0
add esp, 4
jmp main_end_8
main_end_8:
add esp, 0
pop ebp
ret
