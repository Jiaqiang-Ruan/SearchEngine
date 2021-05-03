#!/usr/bin/python

#
#  This python script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
#
import os
import requests
import pickle as pkl
from collections import defaultdict

def getCMD(param_path):
    ROOT="/Users/jiaqiangruan/Projects/SearchEngine/Homeworks"
    HW="HW6"
    LIB=ROOT+"/lucene-8.1.1"
    CLASSPATH="%s/*:%s/%s" % (LIB, ROOT, HW)
    PARAM_DIR="%s/%s/PARAM_DIR" %(ROOT, HW)
    cmd = "java -classpath %s QryEval %s/%s" % (CLASSPATH, PARAM_DIR, param_path)
    return cmd


def get_trec_eval(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'
    hwId = 'HW5'
    qrels = 'cw09a.adhoc.1-200.qrel.indexed'

    #  Form parameters - these must match form parameters in the web page

    url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi'
    values = { 'hwid' : hwId,				# cgi parameter
               'qrel' : qrels,				# cgi parameter
               'logtype' : 'Detailed',			# cgi parameter
               'leaderboard' : 'No'				# cgi parameter
               }

    #  Make the request

    files = {'infile' : (output_path, open(output_path, 'rb')) }	# cgi parameter
    result = requests.post (url, data=values, files=files, auth=(userId, password))

    #  Replace the <br /> with \n for clarity

    # print (result.text.replace ('<br />', '\n'))
    data = result.text
    # print(data)
    # while 1: pass
    # for line in data:
    #     if "P_10" in line:
    #         print(line)
    
    data = result.text
    data = data[data.index('<pre>'):data.index('</pre>')]
    data = data.split('\n')

    print("--output: %s--\n"%output_path)
    for line in data:
        print(line.strip())
    ans = {}
    for line in data:
        if 'P_10 ' in line:
            ans['P@10'] = float(line.split()[2])
        if 'P_20 ' in line:
            ans['P@20'] = float(line.split()[2])
        if 'P_30 ' in line:
            ans['P@30'] = float(line.split()[2])
        if 'map ' in line:
            ans['MAP'] = float(line.split()[2])
        if 'ndcg_cut_10 ' in line:
            ans['ndcg_cut_10'] = float(line.split()[2])
        if 'ndcg_cut_20 ' in line:
            ans['ndcg_cut_20'] = float(line.split()[2])
        if 'ndcg_cut_30 ' in line:
            ans['ndcg_cut_30'] = float(line.split()[2])
    return ans

def get_ndeval_eval(output_path):
    userId = 'jruan@andrew.cmu.edu'
    password = 'Fu5P5isZ'

    #  Form parameters - these must match form parameters in the web page

    url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/nes.cgi'
    values = {'qrel' : 'cw09a.diversity.1-100.qrel.indexed',
              'hwid' : 'HW5'
             }

    #  Make the request

    files = {'infile' : (output_path, open(output_path, 'rb')) }
    result = requests.post (url, data=values, files=files, auth=(userId, password))

    data = result.text
    
    data = data[data.index('<pre>')+len('<pre>'):data.index('</pre>')].strip()
    data = data.split('\n')
    # print(data)

    ans = defaultdict(dict)
    for line in data[1:]:
        tokens = line.split(',')
        print("%s: PIA@10: %f, PIA@20: %f, aNDCG@20: %f"%(tokens[1],float(tokens[18]),float(tokens[19]),float(tokens[13])))
        if tokens[1] != "amean": 
            continue
        if len(tokens) !=23 :continue
        ans['P-IA@10'] = float(tokens[18])
        ans['P-IA@20'] = float(tokens[19])
        ans['aNDCG@20'] = float(tokens[13])

    return ans

total = {}


# params_path = "HW6-Train-0.param"
# output_path = 'OUTPUT_DIR/HW6-Train-0.teIn'
# os.system(getCMD(params_path))
# tmp = get_trec_eval(output_path)


# indexes = ["2.1a", "2.1b", "2.1c", "2.1d",]
# indexes = ["3.1a", "3.1b", "3.1c", "3.1d",]
# indexes = ["4.1a", "4.1b", "4.1c", "4.1d",
#             "4.2a", "4.2b", "4.2c", "4.2d",]
indexes = ["5.1a", "5.1b", "5.1c", "5.1d",]
# indexes = ["5.1c"]

for index in indexes:
    params_path = "HW6-Exp-%s.param" % index
    output_path = 'OUTPUT_DIR/HW6-Exp-%s.teIn' % index
    os.system(getCMD(params_path))
    tmp = get_trec_eval(output_path)
    # tmp.update(get_ndeval_eval(output_path))
    total[index] = tmp

print(total)

with open("result-Exp4.csv", "w+") as f:
    all_exp = indexes
    first_line = ",".join(all_exp)
    f.write(first_line+"\n")

    metrics = ["P@10","P@20","P@30",'ndcg_cut_10','ndcg_cut_20','ndcg_cut_30','MAP']
    for m in metrics:
        res = []
        for index in all_exp:
            res.append("%.4f"%total[index][m])
        line = ",".join(res)
        f.write(line+"\n")