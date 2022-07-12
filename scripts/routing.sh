NRUNS=1
MYJAVAPATH="../"

# Compile
cd $MYJAVAPATH
javac -nowarn lpmaker/ProduceLP.java
cd -

#switches=80
#port=64
#numsvr=3072
#numsvr=2988
#numspinesw=16

switches=6
port=4
numsvr=8
numspinesw=2

topology=rrg
#topology=dring
#topology=leafspine
#graphtype=23
#graphtype=24
graphtype=25
#graphfile=graphfiles/rrg_instance1_80_64.edgelist
#graphfile=graphfiles/dring_instance1_80_64.edgelist
graphfile=graphfiles/test_instance1_80_64.edgelist

method=2
declare -a rs=("opt")
# declare -a rs=("ecmp" "su2" "su3" "fhi" "16disjoint" "32disjoint" "16short" "32short" "100random3" "racke" "wracke" "opt")
#declare -a rs=("ecmp" "su2" "su3" "32disjoint" "32short")

#trafficmode=105
trafficmode=205
a=1
b=1
trafficfile=none
timeframestart=0
timeframeend=0

tag=

isOptimal=false
isEqualShare=false
shouldAvoidHotRacks=false
isPathWeighted=false
pathweightfile=none
#pathweightfile=../WTHelpers/yatesfiles/pathweightfiles/racke.txt

for routing in "${rs[@]}"
do

suffix="$topology"_"$routing"_"$trafficmode"_"$isEqualShare"_"$shouldAvoidHotRacks"_"$isPathWeighted"_"$tag"
netpathfile=netpathfiles/netpath_"$routing"_"$topology".txt
if [ "$routing" = "racke" ] || [ "$routing" = "wracke" ]
then
  netpathfile=../WTHelpers/yatesfiles/netpathfiles/racke.txt
fi
if [ "$routing" = "opt" ]
then
  isOptimal=true
fi

rm -rf ../resultfiles/result_"$suffix".txt
rm -rf flowtmp_"$suffix" pl_"$suffix"

for (( i=0 ; i < $NRUNS ; i++ ))
do

  randSeed=`expr $i + 1`

  # We checked -- the fat-tree does give throughput = 1 each time, as expected. So need to run the LP for it!
  cd $MYJAVAPATH
  java lpmaker/ProduceLP 1 $graphtype $graphfile $trafficmode $switches $port 0 0 $numsvr 0.0 0 0 0 0 0 0 0 0 0 1 $randSeed $trafficfile $isOptimal $netpathfile $isEqualShare $a $b $shouldAvoidHotRacks $isPathWeighted $pathweightfile "$timeframestart" "$timeframeend" $numspinesw

  # Run LP for mynet
  mv my.0.lp topology/my.lp
  mv pl.0 topology/pathlengths/
  cd -

  flowVal=`./lpRun.sh ../topology/my.lp $method`
  rm -rf ../flowIDmap* ../linkCaps* flowIDmap* linkCaps*
  echo "$flowVal" >> flowtmp_"$suffix"
done

avgflow=`cat flowtmp_"$suffix" | awk '{if(NF>0 && $1>=0){sum+=$1; cnt++;}} END{print sum/cnt}'`
echo "$switches $numsvr $port 1 $avgflow" >> ../resultfiles/result_"$suffix".txt

done
