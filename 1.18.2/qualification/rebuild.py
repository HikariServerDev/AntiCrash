#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h, scenarios as sc
h.kill_leftovers(); h.clear_out()
h.script(sc.rebuild_script())
print(h.start("rebuild", 400))
h.clear_out()
