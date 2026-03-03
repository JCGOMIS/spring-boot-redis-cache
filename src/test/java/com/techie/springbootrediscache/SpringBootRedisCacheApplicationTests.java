package com.techie.springbootrediscache;

import com.techie.springbootrediscache.dto.ProductDto;
import com.techie.springbootrediscache.entity.Product;
import com.techie.springbootrediscache.repository.ProductRepository;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class SpringBootRedisCacheApplicationTests {

    @Container
    @ServiceConnection
    static GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:7.4.2"))
            .withExposedPorts(6379);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    ProductRepository repository;
    @Autowired
    private CacheManager manager;
    @MockitoSpyBean
    private ProductRepository productRepositorySpy;
    private final ObjectMapper mapper;

    SpringBootRedisCacheApplicationTests(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @BeforeEach

    void setup(){
        repository.deleteAll();
    }
    @Test
    void testCreateProductAnCacheIt() throws Exception {

        ProductDto productDto = new ProductDto(null,"laptop", BigDecimal.valueOf(1200L));

        //Step1: Create a Product
        MvcResult result = mockMvc.perform(post("/api/product")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(productDto)))
                .andExpect(status().isCreated())
                .andReturn();

        ProductDto createdProduct = mapper.readValue(result.getResponse().getContentAsString(), ProductDto.class);
        Long productId = createdProduct.id();

        //step 2: Check Product exists in DB
        Assertions.assertTrue(repository.findById(productId).isPresent());

        //step3 Check cache
        Cache cache = manager.getCache("PRODUCT_CACHE");

        assertNotNull(cache);
        assertNotNull(cache.get(productId, ProductDto.class));

    }
    @Test
    void testGetProductAndVerifyCache()throws Exception{
        //Step 1 save product in DB
        Product product = new Product();
        product.setName("phone");
        product.setPrice(BigDecimal.valueOf(800L));
        repository.save(product);
        //Step2 fetch product
        mockMvc.perform(MockMvcRequestBuilders.get
                        ("/api/product" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("phone"));
        Mockito.verify(productRepositorySpy, Mockito.times(1)).findById(product.getId());

        Mockito.clearInvocations(productRepositorySpy);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/product" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("phone"));
        Mockito.verify(productRepositorySpy,Mockito.times(0)).findById(product.getId());


    }
    @Test
    void testUpdateProductAndVerifyCache()throws Exception{
        //Step 1 Create and save product
        Product product = new Product();
        product.setName("Tablet");
        product.setPrice(BigDecimal.valueOf(500L));
         repository.save(product);


         ProductDto updatedProductDto = new ProductDto(product.getId(),"Updated tablet", BigDecimal.valueOf(550L));

         //Step 2: update product
        mockMvc.perform(MockMvcRequestBuilders.put("/api/product")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updatedProductDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated tablet"))
                .andExpect(jsonPath("$.price").value(550.0));

        //Step 3: Verify Cache is Updated
        Cache cache = manager.getCache("PRODUCT_CACHE");
        assertNotNull(cache);
        ProductDto cachedProduct = cache.get(product.getId(),ProductDto.class);
        assertNotNull(cachedProduct);

        Assertions.assertEquals("Updated tablet", cachedProduct.name());

    }
    @Test
    void testDeleteProductAndEvictCache()throws Exception{
        //Create and save product

        Product product = new Product();
        product.setName("Smartwatch");
        product.setPrice(BigDecimal.valueOf(250L));
        repository.save(product);

        //Step 2 Delete Product
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/product" + product.getId()))
                .andExpect(status().isNoContent());

        //Step3 check that product is deleted from DB
        Assertions.assertFalse(repository.findById(product.getId()).isPresent());

        //Step 4 Check Cache Eviction

        Cache cache  = manager.getCache("PRODUCT_CACHE");
        assertNotNull(cache);
        Assertions.assertNotNull(cache.get(product.getId()));
    }

}
