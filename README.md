[![Build Status](https://github.com/trackmate-sc/TrackMate-Oneat/actions/workflows/build.yml/badge.svg)](https://github.com/trackmate-sc/TrackMate-Oneat/actions/workflows/build.yml)

# TrackMate-Oneat

TrackMate extension for oneat for verifying lineage trees

## Overview

Oneat is a keras based action classification library written by us and TrackMate-oneat is its Fiji extension that uses the results of detection of such cell events to correct lineage trees in TrackMate.

# Tracking Metrics

Linking max distance: 14 micron, Gap closing max distance: 16 micron, Gap closing max fra me gap: 3, splitting max distance: 15
Initial tracks: 570

### Simple LAP tracker

{DET : 0.9968,    CT : 0.7221,   TRA : 0.9945,    TF : 0.9808,  BCi : 0}

### Simple LAP tracker + Oneat

{DET : 0.9968,    CT : 0.7247,   TRA : 0.9945,    TF : 0.9771,  BCi : 0.3404}

### LAP Tracker with track splitting 

{DET : 0.9968,    CT : 0.454,   TRA :	0.9908;  TF : 0.8830,  BCi :	0.063}

### LAP Tracker with track splitting  + Oneat

{DET : 0.9968,    CT :	0.456,    TRA : 0.9636,   TF :	0.8713,   BCi : 0.2857}

## Videos

In this video I compare the tracking results using LAP tracker of TrackMate and SimpleLap tracker + oneat correction. 
[LAP tracker comparision with SimpleLAP Tracker + oneat correction](https://youtu.be/9HZvWxr2fsY)
