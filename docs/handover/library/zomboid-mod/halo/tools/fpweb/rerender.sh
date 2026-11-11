cd /tmp/claude-0/fpweb
W="assault_bomb assault_rifle ball concussion_rifle dmr energy_sword flag flak_cannon focus_rifle gravity_hammer grenade_launcher magnum needle_rifle needler plasma_launcher plasma_pistol plasma_repeater plasma_rifle rocket_launcher shotgun sniper_rifle spartan_laser spike_rifle"
(for w in $(echo $W | tr ' ' '\n' | sed -n '1~3p'); do node run.js $w six "&fov=49&noup=1"; done) > logR1.txt 2>&1 &
(for w in $(echo $W | tr ' ' '\n' | sed -n '2~3p'); do node run.js $w six "&fov=49&noup=1"; done) > logR2.txt 2>&1 &
(for w in $(echo $W | tr ' ' '\n' | sed -n '3~3p'); do node run.js $w six "&fov=49&noup=1"; done; node run.js target_laser six "&fov=49&noup=1&wp=target_laser") > logR3.txt 2>&1 &
wait
sed 's/&fov=49&/\&fov=49\&noup=1\&/' nobles.sh | grep '^node' > nobles2.sh
(sed -n '1,4p' nobles2.sh | sh) > logR4.txt 2>&1 &
(sed -n '5,7p' nobles2.sh | sh) > logR5.txt 2>&1 &
wait
echo ALLDONE
