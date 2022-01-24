#https://senthilprasanth.atlassian.net/wiki/spaces/RESTAPI/pages/196609/First+Page

import requests
import json
import re
import os
from bs4 import BeautifulSoup

pageNo = 196609
url = f"https://senthilprasanth.atlassian.net/wiki/rest/api/content/{pageNo}"

def putConfPage():
    storageValue = getResponse.json()['body']['storage']['value']
    searchValue = "<td><p>Senthil</p></td><td><p>(.*?)</p></td>"
    patternValue = re.search(rf"{searchValue}", f"{storageValue}").group(1)
    inputValue="India"
    doc = BeautifulSoup(f"<td><p>Senthil</p></td><td><p>{patternValue}</p>", "html.parser")
    doc.find(text={patternValue}).replace_with(inputValue)
    doc = str(doc)
    doc = doc.replace('<html><body>', '').replace('</body></html>', '')
    finalValue = re.sub(f"{searchValue}", f"{doc}", f"{storageValue}")  
    headers = {'Content-Type': 'application/json',}
    pageId = getResponse.json()['id']
    pageType = getResponse.json()['type']
    pageTitle = getResponse.json()['title']
    pageSpace = os.path.basename(getResponse.json()['_expandable']['space'])
    pageNumber = getResponse.json()['version']['number'] + 1
    rawData = {"id":f"{pageId}", \
            "type":f"{pageType}", \
            "title":f"{pageTitle}", \
            "space":{"key":f"{pageSpace}"}, \
            "body":{"storage": \
            {"value":f"<p>{finalValue}</p>", \
            "representation":"storage"}},"version":{"number":f"{pageNumber}"}}
    jsonData = json.dumps(rawData)
    
    if ({inputValue}!={patternValue}):
        putResponse = requests.put(url, headers=headers, data=jsonData, auth=('senthilprasanth@gmail.com', 'bDssdGCCdzCHScUulkF0C3AA'))
        if putResponse.status_code == 200:
            print(f"Success: PUT Request successfully received & Confluence Page {url} updated successfully")
        else:
            print(f"Error: PUT Request to {url} failed. Please triage the error")
    else:
        print(f"Nothing to update")        

getResponse = requests.get(url, params=(('expand', 'body.storage,version'),), auth=('senthilprasanth@gmail.com', 'bDssdGCCdzCHScUulkF0C3AA'))

if getResponse.status_code == 200:
	print(f"Success: GET Request successfully received, understood, and accepted")
	putConfPage()
else:
	print(f"Error: GET Request to {url} failed. Please triage the error")