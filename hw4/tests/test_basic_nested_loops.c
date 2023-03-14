#include <stdio.h>

int main() {
  printf("start of program\n");
  
  for (int i=0; i<3; i++) {
    printf("inside first loop, outermost; i=%d\n", i);
    
    for (int j=0; j<2; j++) {
      printf("inside first loop, inner; j=%d\n", j);
      printf("inside first loop, inner; second printf call\n");
      
      for (int k=0; k<2; k++) {
        printf("inside first loop, innermost; k=%d\n", k);
      }
    }
  }
  
  for (int i=0; i<3; i++) {
    printf("inside second loop, outermost; i=%d\n", i);
    
    for (int j=0; j<2; j++) {
      printf("inside second loop, inner; j=%d\n", j);
      
      for (int k=0; k<2; k++) {
        printf("inside seoncd loop, innermost; k=%d\n", k);
        printf("inside seoncd loop, innermost; second printf call\n");
      }
    }
	
	for (int l=0; l<2; l++) {
        printf("inside seoncd loop, inner, no.2; l=%d\n", l);
        printf("inside seoncd loop, inner, no.2; second printf call\n");
      }
  }
  return 0;
}