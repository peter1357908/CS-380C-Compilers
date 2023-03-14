#include <stdio.h>
#include <stdatomic.h>

void another_func() {
  printf("in another_func\n");
  
  for (int i=0; i<3; i++) {
    printf("inside another_func loop, outermost; i=%d\n", i);
    
    for (int j=0; j<2; j++) {
      printf("inside another_func loop, inner; j=%d\n", j);
    }
	
    for (int l=0; l<2; l++) {
      printf("inside another_func loop, inner, no.2; l=%d\n", l);
      printf("inside another_func loop, inner, no.2; second printf call\n");
    }
  }
}

void branches() {
  printf("in branches\n");
  
  for (int i=0; i<3; i++) {
    printf("inside branches loop, outermost; i=%d\n", i);
    
    if (i < 2) {
      printf("inside branches loop IF, outermost; i=%d\n", i);
    }
    
    for (int j=0; j<2; j++) {
      printf("inside branches loop, inner; j=%d\n", j);
      
      if (i < 2) {
        printf("inside branches loop IF, inner; j=%d\n", j);
      }
    }
  }
}

void atomics() {
  printf("in atomics\n");
  
  atomic_int i = 0;
  
  for (; i<3; i++) {
    printf("inside atomics loop, outermost; atomic i=%d\n", i);
    
    if (i < 2) {
      printf("inside atomics loop, outermost; atomic i=%d\n", i);
    }
  }
}

int main() {
  printf("start of program\n");
  
  for (int i=0; i<3; i++) {
    printf("inside main loop, outermost; i=%d\n", i);
    
    for (int j=0; j<2; j++) {
      printf("inside main loop, inner; j=%d\n", j);
      printf("inside main loop, inner; second printf call\n");
    }
  }
  
  another_func();
  branches();
  atomics();
  
  return 0;
}