Tous les fichiers sources sont sur le dossier src. Le serveur et le client sont sur le package fr.upem.net.tcp.nonblocking et les readers sont dans le sous-package readers.

Execution du programme:
Pour lancer le serveur, se placer dans la section bin du projet et éxécuter cette commande:
java fr.upem.net.tcp.nonblocking.ServerChaton [NUMERO DE PORT] [NOM DU SERVEUR]

Pour lancer un client, il faut aussi se placer dans la section bin du projet et éxécuter cela:
java fr.upem.net.tcp.nonblocking.CLientChat localhost [NUMERO DE PORT] ./ [NOM DU CLIENT]

Pour que la communication se fasse, les numéros de ports doivent être pareils.
Une fois la connection faite, pour envoyer un message privé, la commande est la suivante:
@[NOM DU CLIENT DESTINATAIRE]:[NOM DU SERVEUR DESTINATAIRE] [MESSAGE]
Pour envoyer un message publique, il n'y a aucune règle à respecter. N'importe quel caractère entré est considéré comme un message publique.


