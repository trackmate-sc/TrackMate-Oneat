# TrackMate-oneat
TrackMate extension for oneat for verifying lineage trees

## Overview
Oneat is a keras based action classification library written by us and TrackMate-oneat is its Fiji extension that uses the results of detection of such cell events to correct lineage trees in TrackMate.

## Tracking Metrics

# Simple LAP tracker + Oneat

{DET : 0.9964,    CT : 0.73531,   TRA : 0.9933,    TF : 0.97518,  BCi : 0.10526}

# LAP Tracker with track splitting and Quality as additional cost

{DE : 0.9900,    CT : 0.677033,   TRA :	0.986785;  TF : 0.95041,  BCi :	0.04347}

# LAP Tracker with track splitting and Quality as additional cost + Oneat

{DET : 0.98911,    CT :	0.672629,    TRA : 0.985774,   TF :	0.948692,   BCi : 0.05555}

# LAP Tracker without track splitting and Quality as additional cost + Oneat
{DET : 0.990083,   CT :	0.6766,      TRA : 0.986742, TF : 0.9521, BCi	0.054}


## Videos
In this video I compare the tracking results using LAP tracker of TrackMate and SimpleLap tracker + oneat correction. 
[LAP tracker comparision with SimpleLAP Tracker + oneat correction](https://youtu.be/9HZvWxr2fsY)
