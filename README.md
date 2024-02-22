# best-ppn-api

Vous êtes sur le README usager. Si vous souhaitez accéder au README développement, veuillez suivre ce lien : [README-developpement](README-developpement.md)

best-ppn-api est une API permettant de : 
1. lire des lignes kbart à partir d'un topic Kafka (alimenté par l'API kbart2kafka [lien github](https://github.com/abes-esr/kbart2kafka))
2. calculer le best ppn pour chaque ligne et de l'inscrire sur la ligne en cours de traitement
3. d'envoyer les lignes vers de nouveaux topics pour traitement ultérieur (insertion dans la base de Bacon, mise à jour de notices dans le Sudoc)
4. d'exposer un web service permettant de calculer le best ppn pour une ligne kafka donnée.

## Lecture, calcul et envoie des lignes kbart à partir d'un topic Kafka
La lecture, le calcul et l'envoie d'une ligne kbart à partir d'un topic Kafka sont des processus automatiques.
Ils seront exécutés automatiquement et en parallèle d'une exécution de l'API kbart2kafka ([lien vers le github de kbart2kafka](https://github.com/abes-esr/kbart2kafka).

## Web Service (ws) bestPpn
Le Web Service (ws) bestPpn permet de calculer un best ppn via les informations transmises. Ces informations doivent être du même type que celles disponibles sur une ligne d'un fichier kbart.

### Exemple de requête : 

`http://`[placer ici l'url d'accès à l'application sur votre serveur]`/api/v1/bestPpn?log=true&provider=SPRINGER&publication_type=monograph&publication_title=Theories%20de%20l%27information&online_identifier=978-3-540-37798-6&print_identifier=&title_url=https://link.springer.com/10.1007/BFb0065764&date_monograph_published_online=1974&date_monograph_published_print=1974&first_author=`

La requête possède un paramètre `log` de type booléen (`true` ou `false`). Il permet d'afficher ou non les messages de log dans le résultat, en plus du best ppn (s'il a été déterminé par l'API). 
La requête doit obligatoirement posséder un paramètre `provider`, mais si le champ associé est vide.

### Exemples de résultat :

1. Dans un navigateur internet : 
   - log à true
    ```xml
    <BestPpnDto>
        <ppn>155212915</ppn>
        <typeSupport>ELECTRONIQUE</typeSupport>
        <logs>
            <logs>Entrée dans onlineId2Ppn</logs>
            <logs>
                url : https://www.sudoc.fr/services/onlineId2ppn/monograph/978-3-540-37798-6/SPRINGER / ppn(s) : [PpnWithTypeDto(ppn=155212915, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=true), PpnWithTypeDto(ppn=233094091, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=false)] / erreur(s) : []
            </logs>
            <logs>
                PPN Electronique : PpnWithTypeDto(ppn=155212915, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=true) / score : 5
            </logs>
            <logs>
                Le PPN PpnWithTypeDto(ppn=233094091, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=false) n'a pas de provider trouvé
            </logs>
            <logs>Entrée dans doi2ppn</logs>
            <logs>
                url : https://www.sudoc.fr/services/doi2ppn?provider=SPRINGER&doi=10.1007/BFb0065764 / ppn(s) : [] / erreur(s) : []
            </logs>
        </logs>
    </BestPpnDto>
    ```

   - log à false
    ```xml
    <BestPpnDto>
        <ppn>155212915</ppn>
        <typeSupport>ELECTRONIQUE</typeSupport>
    </BestPpnDto>
    ```

2. Dans une application dédiée (type Postman) : 
   - log à true
    ```json
    {
      "ppn": "155212915",
      "typeSupport": "ELECTRONIQUE",
      "logs": [
        "Entrée dans onlineId2Ppn",
        "url : https://www.sudoc.fr/services/onlineId2ppn/monograph/978-3-540-37798-6/SPRINGER / ppn(s) : [PpnWithTypeDto(ppn=155212915, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=true), PpnWithTypeDto(ppn=233094091, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=false)] / erreur(s) : []",
        "PPN Electronique : PpnWithTypeDto(ppn=155212915, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=true) / score : 5",
        "Le PPN PpnWithTypeDto(ppn=233094091, typeSupport=ELECTRONIQUE, typeDocument=MONOGRAPHIE, providerPresent=false) n'a pas de provider trouvé",
        "Entrée dans doi2ppn",
        "url : https://www.sudoc.fr/services/doi2ppn?provider=SPRINGER&doi=10.1007/BFb0065764 / ppn(s) : [] / erreur(s) : []"
      ]
    }
    ```

   - log à false
    ```json
    {
      "ppn": "155212915",
      "typeSupport": "ELECTRONIQUE"
    }
    ```
