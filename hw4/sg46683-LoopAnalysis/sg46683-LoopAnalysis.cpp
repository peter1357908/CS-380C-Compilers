//===- sg46683-LoopAnaylsis.cpp - Toy LLVM Pass for CS 380C ---------------===//
//
// Made for assignment 4 of the CS 380C Compilers course.
// (Spring 2023, UT Austin) 
//
//===----------------------------------------------------------------------===//
//
// This file implements a toy loop analyzer according to the assignment.
//
//===----------------------------------------------------------------------===//

#include "llvm/Support/raw_ostream.h"
#include "llvm/Pass.h"
#include "llvm/Analysis/LoopPass.h"
#include "llvm/Transforms/Utils/LoopUtils.h"
#include "llvm/Analysis/LoopInfo.h"
using namespace llvm;

#define DEBUG_TYPE "sg46683-LoopAnalysis"

namespace {
  struct ToyLoopAnalysis : public LoopPass {
    static char ID;
    ToyLoopAnalysis() : LoopPass(ID) {}
    
    unsigned int loopCount = 0;

    // help function to test if an instruction is a branch instruction
    bool isBranchInstr(Instruction &instr) {
      // alternatively, `switch` on Opcode like the implementation
      // of Instruction::isAtomic()
      return (isa<BranchInst>(instr) ||
              isa<IndirectBrInst>(instr) ||
              isa<SwitchInst>(instr));
    }

    bool runOnLoop(Loop *L, LPPassManager &LPM) override {
      unsigned int numSubLoopBB = 0;
      unsigned int numTotalBB = 0;
      unsigned int numInstr = 0;
      unsigned int numAtomicInstr = 0;
      unsigned int numTotalBranchInstr = 0;
      unsigned int numSubLoopBranchInstr = 0;

      // note that this program can be made more efficient if we
      // can easily tell if blocks are in sub-loops or not.
      // Right now we simply count the stats separately for nested loops
      // blocks and all blocks and use the difference between their stats
      // where necessary.

      // iterate over all sub-loops' basic blocks first, in which
      // iterate over all instructions and record the relevant stats.
      // Note that we are relying on the property that loops are either
      // disjoint or entirely nested, so we didn't double-count any of
      // of basic blocks across all nested loop by iterating over all
      // sub-loops without tracking which blocks we already visited.
      // Also, the sub-loops only include the top level sub-loops.
      for (Loop *subLoop : L->getSubLoops()) {
        for (BasicBlock *BB : subLoop->getBlocks()) {
          numSubLoopBB++;
          for (Instruction &instr : *BB) {
            if (isBranchInstr(instr)) {
              numSubLoopBranchInstr++;
            }
          }
        }
      }

      // now iterate over ALL basic blocks and the instructions inside.
      for (BasicBlock *BB : L->getBlocks()) {
        numTotalBB++;
        for (Instruction &instr : *BB) {
          numInstr++;
          if (instr.isAtomic()) {
            numAtomicInstr++;
          }
          if (isBranchInstr(instr)) {
            numTotalBranchInstr++;
          }
        }
      }

      errs() << loopCount++;
      // the "header" of a loop is the unique block that dominates all
      // blocks in that loop. Its "parent" is the enclosing method.
      // TOTHINK: is undefined behavior possible here?
      errs() << ": func=" << L->getHeader()->getParent()->getName().str();
      errs() << ", depth=" << L->getLoopDepth() - 1;
      errs() << ", subLoops=" << (L->getSubLoops().empty() ? "false" : "true");
      errs() << ", BBs=" << (numTotalBB - numSubLoopBB);
      errs() << ", instrs=" << numInstr;
      errs() << ", atomics=" << numAtomicInstr;
      errs() << ", branches=" << (numTotalBranchInstr - numSubLoopBranchInstr);
      errs() << '\n';

      // we return `false` because we didn't modify the input loop.
      return false;
    }

    // the standard loop analysis usage (just call `getLoopAnalysisUsage()`)
    void getAnalysisUsage(AnalysisUsage &AU) const override {
      getLoopAnalysisUsage(AU);
    }
  };
}

char ToyLoopAnalysis::ID = 0;
static RegisterPass<ToyLoopAnalysis>
X("sg46683-loop-props", "Loop Analysis Pass for Assignment 4 (CS 380C Spring 2023 UT Austin).");
