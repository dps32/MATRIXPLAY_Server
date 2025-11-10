# Fer anar els scripts des de Windows

## WSL

Cal tenir instal·lat Windows Subsystem for Linux, és a dir un terminal Ubuntu a Windows. 

Es pot instal·lar la última versió d'un terminal Ubuntu des de la botiga d'aplicacions de Windows.

## Clau RSA

A l'arxiu de configuració el camí a la clau privada RSA serà d'aquest estil:
```bash
DEFAULT_RSA_PATH="/root/.ssh/id_rsa"
```
**Important**: La ruta de l'arxiu amb la clau privada ha d'estar a l'espai WSL amb permissos 600. 

Això vol dir que no funciona des de *"/mnt/c/?"* perquè són arxius tipus Windows sense permissos UNIX.

**També** és recomanable tenir tot el projecte a l'espai d'arxius WSL per tal que funcionin els permissos d'execuió.

## Format d'arxius

Heu de configurar *Visual Studio Code*, o el vostre editor per fer els salts de linia amb format UNIX i UTF-8.

Si no ho feu, quan editeu els arxius des de Windows, els haureu d'arreglar perquè funcionin amb WSL, feu-ho amb:
```bash
chmod +x *.sh && chmod +x ../*.sh
dos2unix * && dos2unix ../*
```

## Despendències WSL

A la carpeta del projecte Maven caldrà instal·lar:
```bash
sudo apt install git zip unzip dos2unix net-tools
```
