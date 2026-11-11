import sys,json; sys.path.insert(0,'.')
import engine as e, importlib, specs
g=sys.argv[1]; json.dump(e.run(g,specs.SPECS[g]),open(f'd_{g}.json','w')); print('ok')
