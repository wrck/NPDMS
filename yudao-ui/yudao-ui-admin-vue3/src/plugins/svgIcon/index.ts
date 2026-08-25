import 'virtual:svg-icons-register'

import { quicklyValidateIconSet } from '@iconify/utils'
import { addCollection } from '@iconify/vue/offline'
import epIcons from '@iconify/json/json/ep.json'
import faIcons from '@iconify/json/json/fa.json'
import faSolidIcons from '@iconify/json/json/fa-solid.json'
import generatedCollections from './iconify.generated.json'

addCollection(epIcons)
addCollection(faIcons)
addCollection(faSolidIcons)
generatedCollections.forEach((collection) => {
  const validatedCollection = quicklyValidateIconSet(collection)
  if (!validatedCollection) {
    throw new Error(`Invalid generated Iconify collection: ${collection.prefix}`)
  }
  addCollection(validatedCollection)
})
