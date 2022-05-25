# TrackMate-oneat
TrackMate extension for oneat for verifying lineage trees

## Overview
Oneat is a keras based action classification library written by us and TrackMate-oneat is its Fiji extension that uses the results of detection of such cell events to correct lineage trees in TrackMate.

## Tracking Metrics

# Simple LAP tracker + Oneat

DET	0.996442089326268     CT	0.7353126974099811

TRA	0.993380472393561     TF	0.9751831914559107

BCi	0.10526315789473684

# LAP Tracker with track splitting and Quality as additional cost

DET	0.9900832702498108    CT	0.6770334928229665

TRA	0.9867850157965999    TF	0.9504135656182898


BCi	0.043478260869565216

# LAP Tracker with track splitting and Quality as additional cost + Oneat

DET	0.9891198128139839    CT	0.6726296958855098

TRA	0.9857740333985257    TF	0.9486923875792926

BCi	0.05555




# LAP Tracker without track splitting and Quality as additional cost + Oneat

## Videos
In this video I compare the tracking results using LAP tracker of TrackMate and SimpleLap tracker + oneat correction. 
[LAP tracker comparision with SimpleLAP Tracker + oneat correction](https://youtu.be/9HZvWxr2fsY)
