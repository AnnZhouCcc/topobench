NRUNS=1
MYJAVAPATH="../"

# Compile
cd $MYJAVAPATH
javac -nowarn lpmaker/ProduceLP.java
cd -

switches=80
port=64
numsvr=3072 # may not be needed
trafficmode=2

name=test
topology=rrg
routing=su3
suffix="$name"_"$topology"_"$routing"

rm -rf ../resultfiles/result_"$suffix".txt
rm -rf flowtmp_"$suffix" pl_"$suffix"

for (( i=0 ; i < $NRUNS ; i++ ))
do
  # We checked -- the fat-tree does give throughput = 1 each time, as expected. So need to run the LP for it!
  cd $MYJAVAPATH
  java lpmaker/ProduceLP 1 23 "graphfiles/rrg_instance1_80_64.edgelist" $trafficmode $switches $port 0 0 $numsvr 0.0 0 0 0 0 0 0 0 0 0 1 0 "trafficfiles/fb_skewed.data" false "netpathfiles/netpath_su3_rrg.txt"

  # Run LP for mynet
  mv my.0.lp topology/my.lp
  mv pl.0 topology/pathlengths/
  cd -

  flowVal=`./lpRun.sh ../topology/my.lp`
  rm -rf ../flowIDmap* ../linkCaps* flowIDmap* linkCaps*
  echo "$flowVal" >> flowtmp_"$suffix"

  # Run LP for randCompare
#  cd $MYJAVAPATH
#  mv randCompare.0.lp topology/my.lp
#  mv pl_randCompare.0 topology/pathlengths/
#  cd -
#
#  flowVal=`./lpRun.sh ../topology/my.lp`
#  rm -rf ../flowIDmap* ../linkCaps* flowIDmap* linkCaps*
#  echo "$flowVal" >> flowtmp_test_randCompare
done

avgflow=`cat flowtmp_"$suffix" | awk '{if(NF>0){sum+=$1; cnt++;}} END{print sum/cnt}'`
echo "$switches $numsvr $port 1 $avgflow" >> ../resultfiles/result_"$suffix".txt

#avgflow=`cat flowtmp_test_randCompare | awk '{if(NF>0){sum+=$1; cnt++;}} END{print sum/cnt}'`
#echo "$switches $numsvr $port 1 $avgflow" >> ../resultfiles/result_test_randCompare.txt
