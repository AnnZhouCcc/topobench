export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/annzhou/gurobi912/linux64/lib

NRUNS=1
MYJAVAPATH="../"
INTERMEDIATEFILEPATH="../intermediatefiles"

# Compile
cd $MYJAVAPATH
javac -nowarn lpmaker/ProduceLP.java
javac -nowarn lpmaker/ProduceThroughput.java
cd -

switches=96
port=60
numsvr=3456
numspinesw=36
numleafsw=`expr $switches - $numspinesw`

topology=rrg96
graphtype=23
graphfile=/home/annzhou/DRing/src/emp/datacentre/graphfiles/ring_supergraph/new/rrg/snodes16/instance1_96_60.edgelist

#topology=leafspine
#graphtype=24
#graphfile=null

method=2
#declare -a rs=("ecmp" "su2" "su3" "32disjoint" "32short")
declare -a rs=("opt")
#declare -a rs=("ecmp")

#trafficmode=200
#traffic=a2a
#a=0
#b=0
#trafficfile=none
#timeframestart=0
#timeframeend=0

#trafficmode=206
#traffic=clustera
#a=0
#b=0
#trafficfile=a
#timeframestart=0
#timeframeend=86400

trafficmode=210
traffic=16to4-5
a=16
b=4
trafficfile=5
timeframestart=0
timeframeend=0

tag=main

isOptimal=false
isEqualShare=false
shouldAvoidHotRacks=false
isPathWeighted=false
pathweightfile=none

for routing in "${rs[@]}"
do
echo $topology $routing $traffic
suffix="$topology"_"$routing"_"$trafficmode"_"$isEqualShare"_"$shouldAvoidHotRacks"_"$isPathWeighted"_"$tag"
netpathfile=/home/annzhou/DRing/src/emp/datacentre/netpathfiles/netpath_"$routing"_"$topology".txt
if [ "$routing" = "racke0" ] || [ "$routing" = "racke1" ] || [ "$routing" = "racke2" ]
then
  netpathfile=../WTHelpers/yatesfiles/netpath_"$routing"_"$topology".txt
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

  if [ "$isEqualShare" = "false" ] && [ "$isPathWeighted" = "false" ]
  then
    flowVal=`./lpRun.sh ../topology/my.lp $method`
    rm -rf ../flowIDmap* ../linkCaps* flowIDmap* linkCaps*
    #  echo "$flowVal" >> flowtmp_"$suffix"
    cd $INTERMEDIATEFILEPATH
    rm -rf networkthroughput
    echo "$flowVal" >> networkthroughput
    echo $flowVal
    cd -
  fi

  cd $MYJAVAPATH
  #java lpmaker/ProduceThroughput $numleafsw $numspinesw $numsvr $port _"$topology"_"$routing"_"$traffic"
  cd -
done

done
