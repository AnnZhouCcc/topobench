NRUNS=1
MYJAVAPATH="../"

# Compile
cd $MYJAVAPATH
javac -nowarn lpmaker/ProduceLP.java
cd -

switches=80
port=64
numsvr=3072 # may not be needed

topology=rrg
graphfile=graphfiles/"$topology"_instance1_80_64.edgelist

trafficmode=9
a=10
b=0
trafficfile=none

tag=packet_

isOptimal=false
isEqualShare=false
shouldAvoidHotRacks=false
isPathWeighted=false
pathweightfile=none

declare -a rs=("32disjoint")

for routing in "${rs[@]}"
do

suffix="$topology"_"$routing"_"$trafficmode"_"$isEqualShare"_"$shouldAvoidHotRacks"_"$tag"
netpathfile=netpathfilescopy/netpath_"$routing"_"$topology".txt

for (( i=0 ; i < $NRUNS ; i++ ))
do
  randSeed=`expr $i + 1`

  # We checked -- the fat-tree does give throughput = 1 each time, as expected. So need to run the LP for it!
  cd $MYJAVAPATH
  java lpmaker/ProduceLP 1 23 $graphfile $trafficmode $switches $port 0 0 $numsvr 0.0 0 0 0 0 0 0 0 0 0 1 $randSeed $trafficfile $isOptimal $netpathfile $isEqualShare $a $b $shouldAvoidHotRacks $isPathWeighted $pathweightfile
done

done
