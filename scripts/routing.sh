NRUNS=5
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
a=5
b=0
trafficfile=trafficfiles/fb_skewed.data

isOptimal=false
isEqualShare=true
shouldAvoidHotRacks=true

declare -a rs=("ecmp" "su2" "su3" "fhi" "16disjoint" "32disjoint" "16short" "32short")
# declare -a rs=("opt")

for routing in "${rs[@]}"
do

suffix="$topology"_"$routing"_"$trafficmode"_"$isOptimal"_"$isEqualShare"_"$shouldAvoidHotRacks"
netpathfile=netpathfilescopy/netpath_"$routing"_"$topology".txt
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
  java lpmaker/ProduceLP 1 23 $graphfile $trafficmode $switches $port 0 0 $numsvr 0.0 0 0 0 0 0 0 0 0 0 1 $randSeed $trafficfile $isOptimal $netpathfile $isEqualShare $a $b $shouldAvoidHotRacks

  # Run LP for mynet
  mv my.0.lp topology/my.lp
  mv pl.0 topology/pathlengths/
  cd -

  flowVal=`./lpRun.sh ../topology/my.lp`
  rm -rf ../flowIDmap* ../linkCaps* flowIDmap* linkCaps*
  echo "$flowVal" >> flowtmp_"$suffix"
done

avgflow=`cat flowtmp_"$suffix" | awk '{if(NF>0 && $1>=0){sum+=$1; cnt++;}} END{print sum/cnt}'`
echo "$switches $numsvr $port 1 $avgflow" >> ../resultfiles/result_"$suffix".txt

done
