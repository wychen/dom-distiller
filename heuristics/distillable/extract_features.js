return (function() {
  function schemaOrgTypes() {
    var types = {};
    var elems = document.querySelectorAll(
        '[itemscope][itemtype]');
    for (var i = 0; i < elems.length; i++) {
      if (elems[i].attributes) {
        var name = elems[i].attributes.itemtype.value.split('/').pop();
        if (!(name in types)) {
          types[name] = 0;
        }
        types[name]++;
      }
    }
    return types;
  }

  function sumOfDict(dict) {
    var ans = 0;
    for (var i in dict) {
      ans += dict[i];
    }
    return ans;
  }

  function sortNumber(a,b) {
      return b - a;
  }

  function largestAreaRatio(elems) {
    if (elems.length == 0) {
      return 2;
    }
    var areas = [];
    var totArea = 0;
    for (var i = 0; i < elems.length; i++) {
      var elem = elems[i];
      var area = elem.offsetWidth * elem.offsetHeight;
      areas.push(area);
      totArea += area;
    }
    areas.sort(sortNumber);
    if (totArea == 0) return 2;
    var ans = areas[0] / totArea;
    //console.debug(areas, totArea);
    //console.debug(ans);
    return ans;
  }

  function largestArea(elems) {
    if (elems.length == 0) {
      return 0;
    }
    var areas = [];
    for (var i = 0; i < elems.length; i++) {
      var elem = elems[i];
      areas.push(elem.offsetWidth * elem.offsetHeight);
    }
    areas.sort(sortNumber);
    var ans = areas[0] / body.scrollWidth / body.scrollHeight;
    return ans;
  }

  function hasOGArticle() {
    var elems = document.head.querySelectorAll(
        'meta[property="og:type"],meta[name="og:type"]');
    for (var i = 0; i < elems.length; i++) {
      if (elems[i].content && elems[i].content.toUpperCase() == 'ARTICLE') {
        return true;
      }
    }
    return false;
  }

  function twitterCardType() {
    var elem = document.head.querySelector('meta[name="twitter:card"]');
    if (elem && elem.content) {
      return elem.content
    }
    return "";
  }

  function querySelectorAllLeaf(node, query) {
    var elems = node.querySelectorAll(query);
    var ans = []
    for (var i = 0; i < elems.length; i++) {
      var elem = elems[i];
      if (elem.querySelector(query)) {
        continue;
      }
      ans.push(elem);
    }
    return ans;
  }

  function markRedBorder(elems) {
    for (var i = 0; i < elems.length; i++) {
      var elem = elems[i];
      elem.style.border = '1px red solid';
    }
  }

  function isVisible(e) {
    //var bounds = e.getBoundingClientRect()
    var style = window.getComputedStyle(e);
    return !(
      (e.offsetParent == null && e.offsetHeight == 0 && e.offsetWidth == 0) ||
      //(bounds.height == 0 && bounds.width == 0) ||
      style.display == "none" ||
      style.visibility == "hidden" ||
      style.opacity == 0
    )
  }

  function countVisible(nodes) {
    var count = 0;
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (!isVisible(node)) {
        continue;
      }
      count++;
    }
    return count;
  }

  var unlikelyCandidates = /aside|banner|combx|comment|community|disqus|extra|foot|header|menu|nav|related|remark|rss|share|shoutbox|sidebar|skyscraper|sponsor|ad-break|agegate|pagination|pager|popup/i;
  var okMaybeItsACandidate = /and|article|body|column|main|shadow/i;

  function mozScore() {
    return _mozScore(true, 0.5, 140, true, 1e100);
  }

  function matchPattern(node, regex, checkParents, checkTagName) {
    var n = node;
    var score = 0;
    while(n !== document.body) {
      var matchString = n.id;
      if (checkTagName) matchString += " " + n.tagName;
      if (n.classList.length <= 5) matchString += " " + n.className;
      if (regex.test(matchString)) {
        score++;
      }
      if (!checkParents) return score;
      n = n.parentElement;
    }
    return score;
  }

  function isGoodForScoring(node, checkParents, checkTagName) {
    var n = node;
    var negScore = matchPattern(node, unlikelyCandidates, checkParents, checkTagName);
    var posScore = matchPattern(node, okMaybeItsACandidate, checkParents, checkTagName);
    //console.debug(n, negScore, posScore, "isGoodForScoring");
    if (negScore > 0 &&
        !(posScore > 0)) {
      //console.debug(n, "not match");
      return false;
    }
    return true;
  }

  function countLikely(nodes, checkParents, checkTagName) {
    var count = 0;
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (matchPattern(node, unlikelyCandidates, checkParents, checkTagName)) {
        continue;
      }
      //console.debug(node);
      count++;
    }
    return count;
  }

  function getPeak(hist) {
    if (Object.keys(hist).length == 0) return [];
    var keys = Object.keys(hist);
    //console.debug('keys', keys);
    var peakIdx = keys[0];
    for (var key in hist) {
      if (!hist.hasOwnProperty(key)) {
        continue;
      }
      //console.debug('key', peakIdx, key, hist[peakIdx], hist[key]);
      if (hist[peakIdx].length < hist[key].length) {
        peakIdx = key;
      }
    }
    //console.debug(peakIdx);
    return hist[peakIdx];
  }

  function _mozScore(trim, power, cut, excludeLi, checkParents, checkTagName, checkBounds, saturate) {
    var score = 0;

    var nodes = document.querySelectorAll('p,pre')
    var hist = {};
    //console.debug(nodes);
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (!isVisible(node)) {
        //console.debug(node, "not visible");
        continue;
      }
      if (!isGoodForScoring(node, checkParents, checkTagName)) {
        continue;
      }

      if (excludeLi && node.matches && node.matches("li p")) {
        //console.debug(node, "under li");
        continue;
      }

      var textContent = node.textContent;
      if (trim) textContent = textContent.trim();
      var textContentLength = textContent.length;
      //console.debug(node, "length", textContentLength);
      textContentLength = Math.min(saturate, textContentLength)
      if (textContentLength < cut) {
        //console.debug(node, "too short");
        continue;
      }

      var bounds = node.getBoundingClientRect();
      /*
      if (bounds.width < document.body.offsetWidth * 0.5) {
        console.debug(node, "too narrow", bounds.width, document.body.offsetWidth);
        continue;
      }
      */

      if (checkBounds) {
        var idx = (bounds.left << 16) | bounds.width;
        if (hist[idx] === undefined) {
          hist[idx] = [];
        }
        hist[idx].push(node);
        continue;
      }
      score += Math.pow(textContentLength - cut, power);
    }
    if (checkBounds) {
      console.debug(hist);
      var peak = getPeak(hist);
      console.debug(peak);
      markRedBorder(peak);
      for (var i = 0; i < peak.length; i++) {
        var node = peak[i];
        console.debug(node, "chosen");
        score += Math.pow(node.textContent.length - cut, power);
        console.debug("score", score);
      }
    }
    return score;
  }

  var body = document.body;
  var sections = querySelectorAllLeaf(body, 'section');
  var articles = querySelectorAllLeaf(body, 'article');
  var entries = querySelectorAllLeaf(body, '[class*="post"],[class*="article"],[class*="news"],[id*="post"],[id*="article"],[id*="news"]');
  var types = schemaOrgTypes();
  var twitterType = twitterCardType();
  //markRedBorder(querySelectorAllLeaf(body, 'div'));

  var features = {
     'opengraph': hasOGArticle(),
     'schemaOrgTypes': types,
     'schemaOrgArticle': /Article/.test(Object.keys(types).join()),
     'schemaOrgNews': /News/.test(Object.keys(types).join()),
     'schemaOrgBlog': /Blog/.test(Object.keys(types).join()),
     'schemaOrgPosting': /Posting/.test(Object.keys(types).join()),
     'schemaOrgAllArticle': /Article|Blog|Report|Posting/.test(Object.keys(types).join()),
     'schemaOrgPerson': /Person/.test(Object.keys(types).join()),
     'schemaOrgImage': /Image/.test(Object.keys(types).join()),
     'schemaOrgOrg': /Organization/.test(Object.keys(types).join()),
     'schemaOrgCount': sumOfDict(types),
     'schemaOrgLength': Object.keys(types).length,
     'twitterType': twitterType,
     'twitterSummary': /summary/.test(twitterType),
     'twitterApp': /app/.test(twitterType),
     'url': document.location.href,
     'title': document.title,
     'numElements': body.querySelectorAll('*').length,
     'numAnchors': body.querySelectorAll('a').length,
     'numForms': body.querySelectorAll('form').length,
     'numTextInput': body.querySelectorAll('input[type="text"]').length,
     'numPasswordInput': body.querySelectorAll('input[type="password"]').length,
     'numPPRE': body.querySelectorAll('p,pre').length,
     'numBr': body.querySelectorAll('br').length,
     'numSection': sections.length,
     'numSection2': countLikely(sections, true, false),
     'numSection3': countLikely(sections, true, true),
     'largestSection': largestArea(sections),
     'largestSectionRatio': largestAreaRatio(sections),
     'numArticle': articles.length,
     'numArticle2': countLikely(articles, true, false),
     'numArticle3': countLikely(articles, true, true),
     'largestArticle': largestArea(articles),
     'largestArticleRatio': largestAreaRatio(articles),
     'numEntries': entries.length,
     'numEntries2': countLikely(entries, true, false),
     'numEntries3': countLikely(entries, true, true),
     'largestEntry': largestArea(entries),
     'largestEntryRatio': largestAreaRatio(entries),
     'numH1': body.querySelectorAll('h1').length,
     'numH2': body.querySelectorAll('h2').length,
     'numH3': body.querySelectorAll('h3').length,
     'numH4': body.querySelectorAll('h4').length,
     'innerText': body.innerText,
     'textContent': body.textContent,
     'innerHTML': body.innerHTML,
     'mozScore': Math.min(6 * Math.sqrt(1000 - 140), _mozScore(false, 0.5, 140, true, false, false, false, 1000)),
     'mozScoreLinear': Math.min(6 * 1000, _mozScore(false, 1, 140, true, false, false, false, 1000)),
     'mozScoreAllSqrt': Math.min(6 * Math.sqrt(1000), _mozScore(false, 0.5, 0, true, false, false, false, 1000)),
     'mozScoreAllLinear': Math.min(6 * 1000, _mozScore(false, 1, 0, true, false, false, false, 1000)),
     'mozScore2': Math.min(6 * Math.sqrt(1000 - 140), _mozScore(false, 0.5, 140, true, true, false, false, 1000)),
     'mozScore3': Math.min(6 * Math.sqrt(1000 - 140), _mozScore(false, 0.5, 140, true, true, true, false, 1000)),
     'mozScore4': Math.min(6 * Math.sqrt(1000 - 140), _mozScore(false, 0.5, 140, true, true, true, true, 1000)),
     'visibleElements': countVisible(body.querySelectorAll('*')),
     'visibleAnchors': countVisible(body.querySelectorAll('a')),
     'visiblePPRE': countVisible(body.querySelectorAll('p,pre')),
     'bodyWidth': body.scrollWidth,
     'bodyHeight': body.scrollHeight,
  };

  //console.log(features);
  return features;
})()
