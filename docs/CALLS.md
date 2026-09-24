# Tambola call catalog · v1.5

The app offers English (India), Hindi, and Hinglish number calls. This is a curated family-game catalog, not a claim of one official set of sayings. The 47 formerly plain English calls now use the replacements reviewed in the conversation; the other 43 English scripts retain their existing wording and recordings.

## References and editorial choices

- [PartyStuff: English and Hindi Tambola lingo](https://partystuff.in/tambola-lingo) documents Indian associations such as Independence year, Quit India, shagun/charity, cards and jokers, and Hindi calls. Its Hindi column has gaps and includes transliterations; it is not a complete Hindi script.
- [Tambola Games: number-name traditions](https://www.tambolagames.com/number-names.html) describes regional adaptations including Tees Maar Khan, Chhappan Bhog, cricket and laddoo imagery.
- [Buildmet's Indian Tambola display manual, page 6](https://4.imimg.com/data4/DN/AY/MY-17268396/tambola-bingo-housie-electronic-display.pdf) and [Party Tambola's calling guide](https://www.partytambola.in/tambola-number-calling) were used to cross-check established English calls and Indian variants.
- [Dr Reshma Hingorani's calling collection](https://wholisticwellnessspace.wordpress.com/2026/09/13/knock-at-the-door-number-four-tambola-number-calling-made-fun/) illustrates Roman Hindi plus English digits, including battisi, dozens and cricket references. We do not reproduce its extended caller lines.

Hindi sayings are our concise adaptations of familiar references or English calls where there is no clear Hindi standard. Literal adaptations such as Christmas cake and baker's bun are not described as independently popular Hindi rhymes. Hinglish uses the Hindi phrase followed by English digits and the full English number; the screen shows the phrase in Roman letters. TTS receives the Hindi phrase in Devanagari for pronunciation. The interface and winner-management controls remain in English.

Every clip ends with the full number; no number is identified only by a nickname. Arabic digits remain on the board and in recent calls. Hindi changes number words in the caller, text share and board image. Hinglish text shares include the Roman Hindi phrase. Names of historical events are clues to a number, not statements about the current year or legal ages.

## Recording and verification

Generation uses the existing coral voice and gpt-4o-mini-tts model with language-specific instructions. Hindi is supported by the [OpenAI speech endpoint](https://developers.openai.com/api/docs/guides/text-to-speech); voices are optimized for English, so native-speaker listening remains important. The app discloses AI voice generation. API keys are used locally only.

The manifests record the exact input and SHA-256 for every MP3. The optional local transcription audit sends only the clip and language hint, never the expected script. A number match is a useful check, not a guarantee of every word, accent or native-speaker preference.

## All 90 sayings

| Number | English | Hindi | Hinglish display | Hindi number |
|---:|---|---|---|---|
| 1 | Kelly's eye | खेल की शुरुआत | Khel ki shuruaat | एक |
| 2 | One little duck | एक छोटी बत्तख | Ek chhoti battakh | दो |
| 3 | Cup of tea | छोटा सुखी परिवार | Chhota sukhi parivaar | तीन |
| 4 | Knock at the door | हम दो हमारे दो | Hum do hamare do | चार |
| 5 | High five | पंजाब के पाँच दरिया | Punjab ke paanch dariya | पाँच |
| 6 | Half a dozen | ये लगा छक्का | Yeh laga chhakka | छह |
| 7 | Lucky seven | इंद्रधनुष के रंग | Indradhanush ke rang | सात |
| 8 | Garden gate | बगीचे का फाटक | Bagiche ka phaatak | आठ |
| 9 | Doctor's orders | डॉक्टर की सलाह | Doctor ki salaah | नौ |
| 10 | A perfect ten | दहाई का पहला नंबर | Dahaai ka pehla number | दस |
| 11 | Legs eleven | एक और एक ग्यारह | Ek aur ek gyarah | ग्यारह |
| 12 | One dozen | पूरा एक दर्जन | Poora ek darjan | बारह |
| 13 | Lucky thirteen | बेकर का दर्जन | Baker ka darjan | तेरह |
| 14 | Valentine's Day | राम का वनवास | Ram ka vanvaas | चौदह |
| 15 | Independence Day | स्वतंत्रता दिवस | Swatantrata diwas | पंद्रह |
| 16 | Sweet sixteen | सोलह श्रृंगार | Solah shringaar | सोलह |
| 17 | Dancing queen | नाच की रानी | Naach ki rani | सत्रह |
| 18 | Coming of age | वोट देने की उम्र | Vote dene ki umr | अठारह |
| 19 | Goodbye teens | किशोर उम्र की विदाई | Kishor umr ki vidaai | उन्नीस |
| 20 | One score | दो दहाई पूरी | Do dahaai poori | बीस |
| 21 | Key to the door | तोपों की सलामी | Topon ki salaami | इक्कीस |
| 22 | Two little ducks | दो छोटी बत्तखें | Do chhoti battakhein | बाईस |
| 23 | You and me | तुम और हम | Tum aur hum | तेईस |
| 24 | Two dozen | पूरे दो दर्जन | Poore do darjan | चौबीस |
| 25 | Silver jubilee | चाँदी की सालगिरह | Chaandi ki saalgirah | पच्चीस |
| 26 | Republic Day | गणतंत्र दिवस | Gantantra diwas | छब्बीस |
| 27 | Gateway to heaven | स्वर्ग की राह | Swarg ki raah | सत्ताईस |
| 28 | In a state | फरवरी के दिन | February ke din | अट्ठाईस |
| 29 | In your prime | जवानी की बहार | Jawani ki bahaar | उनतीस |
| 30 | Flirty thirty | तीस मार खाँ | Tees maar khan | तीस |
| 31 | Get up and run | उठो और दौड़ो | Utho aur daudo | इकतीस |
| 32 | All the teeth | बत्तीसी दिखाओ | Battisi dikhao | बत्तीस |
| 33 | All the threes | तीन की जोड़ी | Teen ki jodi | तैंतीस |
| 34 | Ask for more | दिल माँगे और | Dil maange aur | चौंतीस |
| 35 | Jump and jive | झूमो और नाचो | Jhoomo aur naacho | पैंतीस |
| 36 | Three dozen | छत्तीस का आँकड़ा | Chhattis ka aankda | छत्तीस |
| 37 | Mixed luck | किस्मत का खेल | Kismat ka khel | सैंतीस |
| 38 | Christmas cake | क्रिसमस का केक | Christmas ka cake | अड़तीस |
| 39 | Those famous steps | मशहूर सीढ़ियाँ | Mashhoor seedhiyan | उनतालीस |
| 40 | Life begins at forty | ज़िंदगी की नई शुरुआत | Zindagi ki nayi shuruaat | चालीस |
| 41 | Time for fun | मस्ती का समय | Masti ka samay | इकतालीस |
| 42 | Winnie the Pooh | भारत छोड़ो आंदोलन | Bharat chhodo aandolan | बयालीस |
| 43 | Down on your knees | घुटनों के बल | Ghutnon ke bal | तैंतालीस |
| 44 | All the fours | चार की जोड़ी | Chaar ki jodi | चवालीस |
| 45 | Halfway there | आधा सफर पूरा | Aadha safar poora | पैंतालीस |
| 46 | Up to tricks | शरारत का समय | Shararat ka samay | छियालीस |
| 47 | Year of Independence | आज़ादी का साल | Aazaadi ka saal | सैंतालीस |
| 48 | Four dozen | पूरे चार दर्जन | Poore chaar darjan | अड़तालीस |
| 49 | Rise and shine | जागो और चमको | Jaago aur chamko | उनचास |
| 50 | Half a century | आधा शतक पूरा | Aadha shatak poora | पचास |
| 51 | Charity begins | शगुन की रकम | Shagun ki rakam | इक्यावन |
| 52 | A full deck | ताश के पत्ते | Taash ke patte | बावन |
| 53 | Pack with a joker | ताश में एक जोकर | Taash mein ek joker | तिरपन |
| 54 | Clean the floor | ताश में दो जोकर | Taash mein do joker | चौवन |
| 55 | All the fives | पाँच की जोड़ी | Paanch ki jodi | पचपन |
| 56 | Pick up sticks | छप्पन भोग | Chhappan bhog | छप्पन |
| 57 | Heinz varieties | सत्तावन की क्रांति | Sattavan ki kranti | सत्तावन |
| 58 | Make them wait | ज़रा इंतज़ार करो | Zara intezaar karo | अट्ठावन |
| 59 | Brighton line | पचास का आख़िरी नंबर | Pachaas ka aakhri number | उनसठ |
| 60 | Five dozen | पूरे पाँच दर्जन | Poore paanch darjan | साठ |
| 61 | Baker's bun | बेकर की रोटी | Baker ki roti | इकसठ |
| 62 | Turn the screw | पेच घुमाओ | Pech ghumao | बासठ |
| 63 | Tickle me | गुदगुदी करो | Gudgudi karo | तिरसठ |
| 64 | Red raw | लाल सुर्ख रंग | Laal surkh rang | चौंसठ |
| 65 | Old age pension | पेंशन का समय | Pension ka samay | पैंसठ |
| 66 | All the sixes | छक्के पे छक्का | Chhakke pe chhakka | छियासठ |
| 67 | Made in heaven | ऊपरवाले की जोड़ी | Uparwale ki jodi | सड़सठ |
| 68 | Saving grace | लाज बच गई | Laaj bach gayi | अड़सठ |
| 69 | Either way up | उल्टा पुल्टा | Ulta pulta | उनहत्तर |
| 70 | Three score and ten | सात दहाई पूरी | Saat dahaai poori | सत्तर |
| 71 | Bang on the drum | ढोल बजाओ | Dhol bajao | इकहत्तर |
| 72 | Six dozen | पूरे छह दर्जन | Poore chhah darjan | बहत्तर |
| 73 | Queen bee | मधुमक्खियों की रानी | Madhumakkhiyon ki rani | तिहत्तर |
| 74 | Hit the floor | नाचने आओ | Naachne aao | चौहत्तर |
| 75 | Diamond jubilee | हीरक जयंती | Heerak jayanti | पचहत्तर |
| 76 | Trombones | बैंड बजाओ | Band bajao | छिहत्तर |
| 77 | All the sevens | दो हॉकी स्टिक | Do hockey stick | सतहत्तर |
| 78 | Heaven's gate | स्वर्ग का दरवाज़ा | Swarg ka darwaaza | अठहत्तर |
| 79 | One more time | एक बार और | Ek baar aur | उन्यासी |
| 80 | Gandhi's breakfast | अस्सी की लस्सी | Assi ki lassi | अस्सी |
| 81 | Stop and run | रुको फिर दौड़ो | Ruko phir daudo | इक्यासी |
| 82 | Straight on through | सीधे चलते जाओ | Seedhe chalte jao | बयासी |
| 83 | India wins the World Cup | भारत की विश्व कप जीत | Bharat ki World Cup jeet | तिरासी |
| 84 | Seven dozen | पूरे सात दर्जन | Poore saat darjan | चौरासी |
| 85 | Staying alive | खुशी से जीते रहो | Khushi se jeete raho | पचासी |
| 86 | Between the sticks | आख़िरी छक्का | Aakhri chhakka | छियासी |
| 87 | Last of luck | दादाजी का नंबर | Dadaji ka number | सतासी |
| 88 | All the eights | दो गोल लड्डू | Do gol laddoo | अट्ठासी |
| 89 | Nearly there | बस एक बाकी | Bas ek baaki | नवासी |
| 90 | Top of the house | सबसे ऊँचा नंबर | Sabse ooncha number | नब्बे |
