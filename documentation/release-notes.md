# Release notes

Overzicht van wijzigingen per versie van de Publictask-plugin.

## 2.3.0

De URL van de omgeving kan nu in de pluginconfiguratie worden ingevuld. Is die leeg, dan blijft de instelling van de
omgeving zelf (`VALTIMO_URL` of `VALTIMO_APP_HOSTNAME`) gelden.

## 2.2.0
Uploadvelden werken nu in het publieke formulier. Bijlagen die iemand daar meestuurt, komen op dezelfde manier in de zaak terecht als bij een taak binnen GZAC.

## 2.1.3
Valtimo bijgewerkt naar versie 13.41.0.

## 2.1.2
Gegevens uit een zaak worden in het publieke formulier altijd als tekst weergegeven en niet meer als opmaak of script uitgevoerd. Een link naar een publieke taak geeft het formulier daarnaast niet meer terug nadat de taak is afgerond of de uiterste datum is verstreken; links die al zijn verstuurd blijven werken.

## 2.1.1
Bugfixes voor de publieke taak-URL

## 2.1.0
De basis-URL voor publieke taken kan nu ook worden afgeleid van `VALTIMO_APP_HOSTNAME` (met `VALTIMO_APP_SCHEME`). `VALTIMO_URL` blijft leidend.

## 1.0.1
Ondergebracht in een eigen repository met voorbeeldapplicatie, aparte documentatie en een PR-checks workflow. Broncode gesynchroniseerd met de monorepo en ktlint-issues opgelost.

## 1.0.0
Eerste publieke release: taken aanbieden als publiek formulier zonder dat aanvragers hoeven in te loggen.
