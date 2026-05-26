package com.example.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }

    suspend fun getContactsCount(): Int {
        return contactDao.getContactsCount()
    }
}
