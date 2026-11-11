import sys; sys.path.insert(0,'/mnt/project-files/zomboid-mod/tarkov/tools')
import anim
anim.R='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/raw1/EscapeFromTarkov_Data__StreamingAssets__Windows__'
env,tos,clips=anim.load('assets__content__weapons__%s__client_assets.bundle'%sys.argv[1])
print(len(clips)); print(' '.join(sorted(clips)))
